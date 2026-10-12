package com.example.playwin.feature.game.online

import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import com.example.playwin.core.domain.repository.AuthRepository
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.core.domain.repository.RewardsRepository
import com.example.playwin.core.domain.repository.RoomConnection
import com.example.playwin.core.domain.repository.RoomRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject

private const val POLL_EVERY_SECONDS = 2
private const val MAX_CONSECUTIVE_POLL_FAILURES = 3

/**
 * The game-agnostic half of online play, shared by every game's online
 * ViewModel: random matchmaking only - the player can search for *a* random
 * opponent but never choose *which* one, so two people who know each other
 * can't arrange to play each other. The server pairs queued players into a
 * [Room] (see `supabase/migrations/0002_matchmaking.sql`); this controller
 * then mirrors that room live, tracks the opponent's presence, and claims
 * the reward when the room finishes with this player as the winner.
 *
 * What a move *is* stays with each game's ViewModel, which calls
 * [submitMove] with its own encoded board. The room row is the source of
 * truth for whose turn it is - the backend rejects a write unless it's
 * really this player's turn (see `rooms_guard`).
 *
 * Owned by a ViewModel: build it with [Factory.create] passing
 * `viewModelScope`, and call [onCleared] from the ViewModel's own `onCleared`.
 */
class OnlineMatchController(
    private val gameId: GameId,
    private val scope: CoroutineScope,
    private val roomRepository: RoomRepository,
    private val authRepository: AuthRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) {
    class Factory @Inject constructor(
        private val roomRepository: RoomRepository,
        private val authRepository: AuthRepository,
        private val rewardsRepository: RewardsRepository,
        private val couponsRepository: CouponsRepository
    ) {
        fun create(gameId: GameId, scope: CoroutineScope) = OnlineMatchController(
            gameId, scope, roomRepository, authRepository, rewardsRepository, couponsRepository
        )
    }

    private val _state = MutableStateFlow(OnlineMatchState())
    val state: StateFlow<OnlineMatchState> = _state.asStateFlow()

    private val eligibleUserId = CompletableDeferred<String?>()
    private var searchJob: Job? = null
    private var connection: RoomConnection? = null
    private var observeJob: Job? = null
    private var disconnectJob: Job? = null

    // Cleanup must survive the owner's scope being cancelled, which happens as onCleared() starts.
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            val user = authRepository.observeCurrentUser().first()
            val eligibleId = user?.takeIf { !it.isGuest }?.id
            _state.update { it.copy(canPlayOnline = eligibleId != null, myId = eligibleId) }
            eligibleUserId.complete(eligibleId)
        }
    }

    /** Joins the matchmaking queue and polls until paired, the search times out, or it's cancelled. */
    fun findRandomPlayer() {
        val phase = _state.value.phase
        if (phase == OnlinePhase.SEARCHING || phase == OnlinePhase.ROOM) return

        _state.update { it.copy(phase = OnlinePhase.SEARCHING, searchSecondsElapsed = 0, errorMessage = null) }
        searchJob = scope.launch {
            val myId = eligibleUserId.await()
            if (myId == null) {
                _state.update { it.copy(phase = OnlinePhase.IDLE) }
                return@launch
            }

            var consecutiveFailures = 0
            for (second in 0..MATCHMAKING_TIMEOUT_SECONDS) {
                _state.update { it.copy(searchSecondsElapsed = second) }
                if (second % POLL_EVERY_SECONDS == 0) {
                    val result = roomRepository.matchmake(gameId)
                    val room = result.getOrNull()
                    if (room != null) {
                        attach(room, myId)
                        return@launch
                    }
                    consecutiveFailures = if (result.isFailure) consecutiveFailures + 1 else 0
                    if (consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
                        roomRepository.cancelMatchmaking(gameId)
                        _state.update {
                            it.copy(
                                phase = OnlinePhase.IDLE,
                                errorMessage = result.exceptionOrNull()?.message ?: "Matchmaking failed"
                            )
                        }
                        return@launch
                    }
                }
                delay(1000)
            }

            // Timed out. If someone paired with us in this last instant, the
            // server marks that room abandoned so they aren't left hanging.
            roomRepository.cancelMatchmaking(gameId)
            _state.update { it.copy(phase = OnlinePhase.NO_OPPONENT_FOUND) }
        }
    }

    /** Stops searching and leaves the server-side queue. */
    fun cancelSearch() {
        if (_state.value.phase != OnlinePhase.SEARCHING) return
        val job = searchJob
        searchJob = null
        _state.update { it.copy(phase = OnlinePhase.IDLE, searchSecondsElapsed = 0) }
        cleanupScope.launch {
            job?.cancelAndJoin()
            roomRepository.cancelMatchmaking(gameId)
        }
    }

    /** Leaving the online screen entirely: cancels any search and forfeits any in-progress room. */
    fun exit() {
        when (_state.value.phase) {
            OnlinePhase.SEARCHING -> cancelSearch()
            OnlinePhase.ROOM -> leaveRoom()
            else -> Unit
        }
    }

    /** Deliberate exit from a room: forfeits it if still in progress, then returns to the idle state. */
    fun leaveRoom() {
        val room = _state.value.room
        val conn = connection
        connection = null
        observeJob?.cancel()
        disconnectJob?.cancel()
        disconnectJob = null
        val current = _state.value
        _state.value = OnlineMatchState(canPlayOnline = current.canPlayOnline, myId = current.myId)
        cleanupScope.launch {
            if (room?.status == RoomStatus.ACTIVE) conn?.markAbandoned()
            conn?.close()
        }
    }

    fun dismissError() = _state.update { it.copy(errorMessage = null) }

    fun showError(message: String) = _state.update { it.copy(errorMessage = message) }

    /**
     * Runs one move: disables input while [block] resolves and submits it, and
     * ignores the call if a move is already in flight or it isn't this
     * player's turn.
     */
    fun runMove(block: suspend () -> Unit) {
        val current = _state.value
        if (!current.isMyTurn || current.isResolving) return
        _state.update { it.copy(isResolving = true) }
        scope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(isResolving = false) }
            }
        }
    }

    /**
     * Writes a new board [boardState] to the room. [nextTurnPlayer] is 1
     * (host) or 2 (guest), or null when the game is over. A rejection (e.g.
     * not actually this player's turn per the row) is surfaced as an error.
     */
    suspend fun submitMove(
        boardState: JsonElement,
        nextTurnPlayer: Int?,
        status: RoomStatus = RoomStatus.ACTIVE,
        winnerPlayer: Int? = null
    ): Result<Unit> {
        val conn = connection ?: return Result.failure(IllegalStateException("Not in a room"))
        val current = _state.value
        val result = conn.submitMove(
            state = boardState,
            nextTurn = nextTurnPlayer?.let(current::playerId),
            status = status,
            winnerId = winnerPlayer?.let(current::playerId)
        )
        // The exception is a raw network or database error (it can dump the request), so it's never shown.
        result.onFailure { showError("That move didn't go through - check your connection, or it may not be your turn") }
        return result
    }

    fun onCleared() {
        val conn = connection
        val wasSearching = _state.value.phase == OnlinePhase.SEARCHING
        cleanupScope.launch {
            if (wasSearching) roomRepository.cancelMatchmaking(gameId)
            conn?.close()
        }
    }

    private suspend fun attach(room: Room, myId: String) {
        connection?.close()
        val conn = roomRepository.connect(room, myId)
        connection = conn
        _state.update {
            it.copy(phase = OnlinePhase.ROOM, room = room, opponentOnline = false, opponentEverSeen = false)
        }

        observeJob?.cancel()
        observeJob = scope.launch {
            combine(conn.roomUpdates, conn.presentUserIds) { updatedRoom, present ->
                updatedRoom to present
            }.collect { (updatedRoom, present) ->
                val opponentId = if (updatedRoom.hostId == myId) updatedRoom.guestId else updatedRoom.hostId
                val opponentOnline = opponentId != null && opponentId in present
                val alreadyClaimed = _state.value.wonCouponId != null
                _state.update {
                    it.copy(
                        room = updatedRoom,
                        opponentOnline = opponentOnline,
                        opponentEverSeen = it.opponentEverSeen || opponentOnline
                    )
                }
                trackOpponentPresence(conn, updatedRoom, opponentOnline)
                if (updatedRoom.status == RoomStatus.FINISHED && updatedRoom.winnerId == myId && !alreadyClaimed) {
                    claimReward()
                }
            }
        }
    }

    /**
     * While the game is active and the opponent is absent (never connected, or
     * dropped), counts down [OPPONENT_DISCONNECT_GRACE_SECONDS]; if they don't
     * come back in time the room is marked abandoned. Nobody is awarded a win
     * for a disconnect - that would reward pulling the plug on a losing game.
     */
    private fun trackOpponentPresence(conn: RoomConnection, room: Room, opponentOnline: Boolean) {
        if (room.status != RoomStatus.ACTIVE || opponentOnline) {
            disconnectJob?.cancel()
            disconnectJob = null
            _state.update { it.copy(disconnectSecondsLeft = null) }
            return
        }
        if (disconnectJob != null) return
        disconnectJob = scope.launch {
            for (secondsLeft in OPPONENT_DISCONNECT_GRACE_SECONDS downTo 1) {
                _state.update { it.copy(disconnectSecondsLeft = secondsLeft) }
                delay(1000)
            }
            _state.update { it.copy(disconnectSecondsLeft = 0) }
            conn.markAbandoned()
        }
    }

    private suspend fun claimReward() {
        val coupon = rewardsRepository.claimReward(gameId)
        couponsRepository.addCoupon(coupon)
        _state.update { it.copy(wonCouponId = coupon.id) }
    }
}
