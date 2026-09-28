package com.example.playwin.feature.game.connectfour.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.connectfour.Connect4Logic
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import com.example.playwin.core.domain.repository.AuthRepository
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.repository.RewardsRepository
import com.example.playwin.core.domain.repository.RoomConnection
import com.example.playwin.core.domain.repository.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

private const val POLL_EVERY_SECONDS = 2
private const val MAX_CONSECUTIVE_POLL_FAILURES = 3

/**
 * Online multiplayer Connect 4 via random matchmaking only: the player can
 * search for *a* random opponent but never choose *which* one, so two people
 * who know each other can't arrange to play each other. The server pairs
 * queued players into a [Room] (see `supabase/migrations/0002_matchmaking.sql`);
 * this ViewModel then mirrors that room's live state.
 *
 * The room row is the source of truth for whose turn it is - [onColumnClick]
 * optimistically checks locally so the UI feels responsive, but the actual
 * move is only ever accepted by the backend if it's really this player's turn
 * (see `rooms_guard`); a rejected write surfaces as
 * [Connect4OnlineUiState.errorMessage] and the board snaps back to whatever
 * the row says on the next update.
 */
@HiltViewModel
class Connect4OnlineViewModel @Inject constructor(
    private val roomRepository: RoomRepository,
    private val authRepository: AuthRepository,
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(Connect4OnlineUiState())
    val uiState: StateFlow<Connect4OnlineUiState> = _uiState.asStateFlow()

    private val eligibleUserId = CompletableDeferred<String?>()
    private var searchJob: Job? = null
    private var connection: RoomConnection? = null
    private var observeJob: Job? = null
    private var disconnectJob: Job? = null

    // Cleanup must survive viewModelScope's cancellation, which fires as onCleared() starts.
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        viewModelScope.launch {
            val user = authRepository.observeCurrentUser().first()
            val eligibleId = user?.takeIf { !it.isGuest }?.id
            _uiState.update { it.copy(canPlayOnline = eligibleId != null, myId = eligibleId) }
            eligibleUserId.complete(eligibleId)
        }
    }

    /** Joins the matchmaking queue and polls until paired, the search times out, or it's cancelled. */
    fun findRandomPlayer() {
        val phase = _uiState.value.phase
        if (phase == Connect4OnlinePhase.SEARCHING || phase == Connect4OnlinePhase.ROOM) return

        _uiState.update { it.copy(phase = Connect4OnlinePhase.SEARCHING, searchSecondsElapsed = 0, errorMessage = null) }
        searchJob = viewModelScope.launch {
            val myId = eligibleUserId.await()
            if (myId == null) {
                _uiState.update { it.copy(phase = Connect4OnlinePhase.IDLE) }
                return@launch
            }

            var consecutiveFailures = 0
            for (second in 0..MATCHMAKING_TIMEOUT_SECONDS) {
                _uiState.update { it.copy(searchSecondsElapsed = second) }
                if (second % POLL_EVERY_SECONDS == 0) {
                    val result = roomRepository.matchmake(GameId.CONNECT_FOUR)
                    val room = result.getOrNull()
                    if (room != null) {
                        attach(room, myId)
                        return@launch
                    }
                    consecutiveFailures = if (result.isFailure) consecutiveFailures + 1 else 0
                    if (consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
                        roomRepository.cancelMatchmaking(GameId.CONNECT_FOUR)
                        _uiState.update {
                            it.copy(
                                phase = Connect4OnlinePhase.IDLE,
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
            roomRepository.cancelMatchmaking(GameId.CONNECT_FOUR)
            _uiState.update { it.copy(phase = Connect4OnlinePhase.NO_OPPONENT_FOUND) }
        }
    }

    /** Stops searching and leaves the server-side queue. */
    fun cancelSearch() {
        if (_uiState.value.phase != Connect4OnlinePhase.SEARCHING) return
        val job = searchJob
        searchJob = null
        _uiState.update { it.copy(phase = Connect4OnlinePhase.IDLE, searchSecondsElapsed = 0) }
        cleanupScope.launch {
            job?.cancelAndJoin()
            roomRepository.cancelMatchmaking(GameId.CONNECT_FOUR)
        }
    }

    /** Leaving the online screen entirely: cancels any search and forfeits any in-progress room. */
    fun exit() {
        when (_uiState.value.phase) {
            Connect4OnlinePhase.SEARCHING -> cancelSearch()
            Connect4OnlinePhase.ROOM -> leaveRoom()
            else -> Unit
        }
    }

    fun onColumnClick(col: Int) {
        val state = _uiState.value
        val room = state.room ?: return
        val myId = state.myId ?: return
        val myPlayerNumber = state.myPlayerNumber ?: return
        if (!state.isMyTurn || state.isResolving) return

        val newBoard = Connect4Logic.placePiece(room.board, col, myPlayerNumber) ?: return
        _uiState.update { it.copy(isResolving = true) }

        viewModelScope.launch {
            val isBoardFull = Connect4Logic.isBoardFull(newBoard)
            val outcome = gameEngineRepository.resolve(
                GameId.CONNECT_FOUR,
                GameSnapshot.ConnectFour(board = newBoard, lastPlayer = myPlayerNumber, isBoardFull = isBoardFull)
            )
            val opponentId = state.opponentId
            val (nextStatus, nextTurn, winnerId) = when (outcome) {
                GameOutcome.WIN -> Triple(RoomStatus.FINISHED, null, myId)
                GameOutcome.DRAW -> Triple(RoomStatus.FINISHED, null, null)
                GameOutcome.LOSE, GameOutcome.IN_PROGRESS -> Triple(RoomStatus.ACTIVE, opponentId, null)
            }

            connection?.submitMove(newBoard, nextTurn, nextStatus, winnerId)?.onFailure { e ->
                _uiState.update { it.copy(errorMessage = e.message ?: "That move wasn't accepted - it may not be your turn") }
            }
            _uiState.update { it.copy(isResolving = false) }
        }
    }

    /** Deliberate exit from a room: forfeits it if still in progress, then returns to the idle state. */
    fun leaveRoom() {
        val room = _uiState.value.room
        val conn = connection
        connection = null
        observeJob?.cancel()
        disconnectJob?.cancel()
        disconnectJob = null
        resetToIdle()
        cleanupScope.launch {
            if (room?.status == RoomStatus.ACTIVE) conn?.markAbandoned()
            conn?.close()
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    private fun resetToIdle() {
        val current = _uiState.value
        _uiState.value = Connect4OnlineUiState(canPlayOnline = current.canPlayOnline, myId = current.myId)
    }

    private suspend fun attach(room: Room, myId: String) {
        connection?.close()
        val conn = roomRepository.connect(room, myId)
        connection = conn
        _uiState.update {
            it.copy(phase = Connect4OnlinePhase.ROOM, room = room, opponentOnline = false, opponentEverSeen = false)
        }

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(conn.roomUpdates, conn.presentUserIds) { updatedRoom, present ->
                updatedRoom to present
            }.collect { (updatedRoom, present) ->
                val opponentId = if (updatedRoom.hostId == myId) updatedRoom.guestId else updatedRoom.hostId
                val opponentOnline = opponentId != null && opponentId in present
                val alreadyClaimed = _uiState.value.wonCouponId != null
                _uiState.update {
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
            _uiState.update { it.copy(disconnectSecondsLeft = null) }
            return
        }
        if (disconnectJob != null) return
        disconnectJob = viewModelScope.launch {
            for (secondsLeft in OPPONENT_DISCONNECT_GRACE_SECONDS downTo 1) {
                _uiState.update { it.copy(disconnectSecondsLeft = secondsLeft) }
                delay(1000)
            }
            _uiState.update { it.copy(disconnectSecondsLeft = 0) }
            conn.markAbandoned()
        }
    }

    private suspend fun claimReward() {
        val coupon = rewardsRepository.claimReward(GameId.CONNECT_FOUR)
        couponsRepository.addCoupon(coupon)
        _uiState.update { it.copy(wonCouponId = coupon.id) }
    }

    override fun onCleared() {
        val conn = connection
        val wasSearching = _uiState.value.phase == Connect4OnlinePhase.SEARCHING
        cleanupScope.launch {
            if (wasSearching) roomRepository.cancelMatchmaking(GameId.CONNECT_FOUR)
            conn?.close()
        }
    }
}
