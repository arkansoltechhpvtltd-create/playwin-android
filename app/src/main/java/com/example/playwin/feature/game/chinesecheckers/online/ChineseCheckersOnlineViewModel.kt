package com.example.playwin.feature.game.chinesecheckers.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersBoard
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersMove
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersPosition
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersRules
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.feature.game.online.OnlineMatchController
import com.example.playwin.feature.game.online.OnlineMatchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject

/**
 * What online Chinese checkers stores in `rooms.board_state`: whose peg is in
 * each hole, whose turn it is and how many turns have been played (for the
 * anti-spoiling rule), plus the last move, so the other player's screen can
 * show it hopping into place. A fresh room's `{}` is the starting position.
 */
@Serializable
data class ChineseCheckersRoomState(
    val cells: List<Int> = emptyList(),
    val sideToMove: Int = 1,
    val turnsPlayed: Int = 0,
    val lastFrom: Int? = null,
    val lastPath: List<Int> = emptyList(),
    val lastJump: Boolean = false
)

private val json = Json { ignoreUnknownKeys = true }

private fun Room.chineseCheckersRoomState(): ChineseCheckersRoomState? =
    runCatching { json.decodeFromJsonElement<ChineseCheckersRoomState>(state) }.getOrNull()

/** The room's position; a fresh room (or anything unreadable) is the starting position. */
fun Room.chineseCheckersPosition(): ChineseCheckersPosition {
    val stored = chineseCheckersRoomState()
    if (stored == null || stored.cells.size != ChineseCheckersBoard.holes.size) return ChineseCheckersRules.initialPosition()
    return ChineseCheckersPosition(stored.cells, stored.sideToMove, stored.turnsPlayed)
}

/** The move that led to the room's position, or null at the start (or after a pass). */
fun Room.chineseCheckersLastMove(): ChineseCheckersMove? {
    val stored = chineseCheckersRoomState() ?: return null
    val from = stored.lastFrom ?: return null
    val to = stored.lastPath.lastOrNull() ?: return null
    return ChineseCheckersMove(from, to, stored.lastPath, stored.lastJump)
}

fun ChineseCheckersPosition.toRoomJson(lastMove: ChineseCheckersMove?): JsonElement =
    json.encodeToJsonElement(
        ChineseCheckersRoomState(
            cells = cells,
            sideToMove = sideToMove,
            turnsPlayed = turnsPlayed,
            lastFrom = lastMove?.from,
            lastPath = lastMove?.path.orEmpty(),
            lastJump = lastMove?.isJump ?: false
        )
    )

/** Tap-to-select state for the local player; the position itself always comes from the room. */
data class ChineseCheckersSelection(
    val hole: Int? = null,
    val moves: List<ChineseCheckersMove> = emptyList()
)

/**
 * Online Chinese checkers via random matchmaking. The host is player 1 and
 * moves first from the bottom point; the guest is player 2. Each move is one
 * room write that the backend only accepts from the player whose turn it is,
 * and every move is checked by [GameEngineRepository.resolve], which also
 * rules on the win - never decided here. A player with no move at all passes
 * automatically.
 */
@HiltViewModel
class ChineseCheckersOnlineViewModel @Inject constructor(
    controllerFactory: OnlineMatchController.Factory,
    private val gameEngineRepository: GameEngineRepository
) : ViewModel() {

    val controller = controllerFactory.create(GameId.CHINESE_CHECKERS, viewModelScope)
    val uiState: StateFlow<OnlineMatchState> = controller.state

    private val _selection = MutableStateFlow(ChineseCheckersSelection())
    val selection: StateFlow<ChineseCheckersSelection> = _selection.asStateFlow()

    init {
        // Every peg boxed in on your turn: pass, so the game can go on.
        viewModelScope.launch {
            uiState.collect { state ->
                val room = state.room ?: return@collect
                val me = state.myPlayerNumber ?: return@collect
                if (!state.isMyTurn || state.isResolving || room.status != RoomStatus.ACTIVE) return@collect
                val position = room.chineseCheckersPosition()
                if (position.sideToMove != me || ChineseCheckersRules.legalMoves(position).isNotEmpty()) return@collect
                controller.runMove {
                    controller.submitMove(ChineseCheckersRules.pass(position).toRoomJson(lastMove = null), nextTurnPlayer = ChineseCheckersBoard.opponent(me))
                }
            }
        }
    }

    /**
     * A tap on hole [hole]: onto one of the selected peg's destinations plays
     * the move; onto one of your pegs picks it up (or puts it down again);
     * anywhere else puts it down.
     */
    fun onHoleTap(hole: Int) {
        val state = uiState.value
        val room = state.room ?: return
        val me = state.myPlayerNumber ?: return
        if (!state.isMyTurn || state.isResolving) return
        val position = room.chineseCheckersPosition()
        if (position.sideToMove != me) return

        val current = _selection.value
        val move = current.moves.firstOrNull { it.to == hole }
        if (move != null) {
            _selection.value = ChineseCheckersSelection()
            play(position, move, me)
            return
        }
        _selection.value = if (hole != current.hole && position.cells.getOrNull(hole) == me) {
            ChineseCheckersSelection(hole, ChineseCheckersRules.movesFrom(position, hole))
        } else {
            ChineseCheckersSelection()
        }
    }

    private fun play(position: ChineseCheckersPosition, move: ChineseCheckersMove, me: Int) {
        controller.runMove {
            val encoded = ChineseCheckersRules.play(position, move).toRoomJson(move)
            val opponent = ChineseCheckersBoard.opponent(me)
            when (gameEngineRepository.resolve(GameId.CHINESE_CHECKERS, GameSnapshot.ChineseCheckers(position, move.from, move.to))) {
                GameOutcome.WIN -> controller.submitMove(encoded, null, RoomStatus.FINISHED, winnerPlayer = me)
                // The move took the game past the anti-spoiling count with the
                // opponent's goal already full: they've won.
                GameOutcome.LOSE -> controller.submitMove(encoded, null, RoomStatus.FINISHED, winnerPlayer = opponent)
                GameOutcome.DRAW -> controller.submitMove(encoded, null, RoomStatus.FINISHED)
                GameOutcome.IN_PROGRESS -> controller.submitMove(encoded, nextTurnPlayer = opponent)
            }
        }
    }

    override fun onCleared() = controller.onCleared()
}
