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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Online multiplayer Connect 4: creates/joins a [com.example.playwin.core.domain.model.Room]
 * and mirrors its live state. The room row is the source of truth for whose
 * turn it is - [onColumnClick] optimistically checks locally so the UI feels
 * responsive, but the actual move is only ever accepted by the backend if
 * it's really this player's turn (see `rooms_guard` in
 * `supabase/migrations/0001_rooms.sql`); a rejected write surfaces as
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

    private var connection: RoomConnection? = null
    private var observeJob: Job? = null

    // Cleanup must survive viewModelScope's cancellation, which fires as onCleared() starts.
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        viewModelScope.launch {
            val user = authRepository.observeCurrentUser().first()
            val eligibleId = user?.takeIf { !it.isGuest }?.id
            _uiState.update { it.copy(canPlayOnline = eligibleId != null, myId = eligibleId) }
        }
    }

    fun createRoom() {
        val myId = _uiState.value.myId ?: return
        _uiState.update { it.copy(phase = Connect4OnlinePhase.CREATING, errorMessage = null) }
        viewModelScope.launch {
            roomRepository.createRoom(myId).fold(
                onSuccess = { room -> attach(room, myId) },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(phase = Connect4OnlinePhase.MENU, errorMessage = e.message ?: "Couldn't create a room")
                    }
                }
            )
        }
    }

    fun showEnterCode() {
        _uiState.update { it.copy(phase = Connect4OnlinePhase.ENTER_CODE, errorMessage = null) }
    }

    fun joinRoom(code: String) {
        val myId = _uiState.value.myId ?: return
        if (code.isBlank()) return
        _uiState.update { it.copy(phase = Connect4OnlinePhase.JOINING, errorMessage = null) }
        viewModelScope.launch {
            roomRepository.joinRoom(myId, code).fold(
                onSuccess = { room -> attach(room, myId) },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(phase = Connect4OnlinePhase.ENTER_CODE, errorMessage = e.message ?: "Couldn't join that room")
                    }
                }
            )
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

    /** Deliberate exit: marks the room abandoned if the game was still in progress, then returns to the menu. */
    fun leaveRoom() {
        val room = _uiState.value.room
        val conn = connection
        viewModelScope.launch {
            if (room?.status == RoomStatus.ACTIVE || room?.status == RoomStatus.WAITING) {
                conn?.markAbandoned()
            }
            conn?.close()
            connection = null
            observeJob?.cancel()
            val myId = _uiState.value.myId
            val canPlayOnline = _uiState.value.canPlayOnline
            _uiState.value = Connect4OnlineUiState(canPlayOnline = canPlayOnline, myId = myId)
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    private suspend fun attach(room: Room, myId: String) {
        connection?.close()
        val conn = roomRepository.connect(room, myId)
        connection = conn
        _uiState.update { it.copy(phase = Connect4OnlinePhase.ROOM, room = room) }

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(conn.roomUpdates, conn.presentUserIds) { updatedRoom, present ->
                updatedRoom to present
            }.collect { (updatedRoom, present) ->
                val opponentId = if (updatedRoom.hostId == myId) updatedRoom.guestId else updatedRoom.hostId
                val alreadyClaimed = _uiState.value.wonCouponId != null
                _uiState.update {
                    it.copy(
                        room = updatedRoom,
                        opponentOnline = opponentId != null && opponentId in present
                    )
                }
                if (updatedRoom.status == RoomStatus.FINISHED && updatedRoom.winnerId == myId && !alreadyClaimed) {
                    claimReward()
                }
            }
        }
    }

    private suspend fun claimReward() {
        val coupon = rewardsRepository.claimReward(GameId.CONNECT_FOUR)
        couponsRepository.addCoupon(coupon)
        _uiState.update { it.copy(wonCouponId = coupon.id) }
    }

    override fun onCleared() {
        val conn = connection
        cleanupScope.launch { conn?.close() }
    }
}
