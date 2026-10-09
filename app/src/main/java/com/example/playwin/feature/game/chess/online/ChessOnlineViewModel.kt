package com.example.playwin.feature.game.chess.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.chess.ChessEngine
import com.example.playwin.core.domain.chess.ChessFen
import com.example.playwin.core.domain.chess.ChessMove
import com.example.playwin.core.domain.chess.ChessPosition
import com.example.playwin.core.domain.chess.PieceColor
import com.example.playwin.core.domain.chess.PieceType
import com.example.playwin.core.domain.chess.Square
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.feature.game.chess.PendingPromotion
import com.example.playwin.feature.game.chess.promotionFor
import com.example.playwin.feature.game.online.OnlineMatchController
import com.example.playwin.feature.game.online.OnlineMatchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject

/** What online chess stores in `rooms.board_state`: the position as FEN. A fresh room's `{}` is the start position. */
@Serializable
data class ChessRoomState(val fen: String? = null)

private val json = Json { ignoreUnknownKeys = true }

fun Room.chessPosition(): ChessPosition =
    runCatching { json.decodeFromJsonElement<ChessRoomState>(state) }.getOrNull()
        ?.fen?.let(ChessFen::fromFen)
        ?: ChessEngine.initialPosition()

fun ChessPosition.toRoomJson(): JsonElement = json.encodeToJsonElement(ChessRoomState(ChessFen.toFen(this)))

/** Online the host (player 1) plays White and moves first; the guest plays Black. */
fun chessColorOf(playerNumber: Int): PieceColor = if (playerNumber == 1) PieceColor.WHITE else PieceColor.BLACK

/**
 * Tap-to-select state for the local player; the position itself always comes from
 * the room. [promotion] is a pawn move onto the last rank waiting on the player's pick.
 */
data class ChessSelection(
    val square: Square? = null,
    val moves: List<ChessMove> = emptyList(),
    val promotion: PendingPromotion? = null
)

/**
 * Online chess via random matchmaking. Each move is one room write that the
 * backend only accepts from the player whose turn it is; checkmate and
 * stalemate are confirmed by [GameEngineRepository.resolve] from the new
 * position, never decided here.
 */
@HiltViewModel
class ChessOnlineViewModel @Inject constructor(
    controllerFactory: OnlineMatchController.Factory,
    private val gameEngineRepository: GameEngineRepository
) : ViewModel() {

    val controller = controllerFactory.create(GameId.CHESS, viewModelScope)
    val uiState: StateFlow<OnlineMatchState> = controller.state

    private val _selection = MutableStateFlow(ChessSelection())
    val selection: StateFlow<ChessSelection> = _selection.asStateFlow()

    fun onSquareTap(square: Square) {
        val state = uiState.value
        val room = state.room ?: return
        val me = state.myPlayerNumber ?: return
        if (!state.isMyTurn || state.isResolving) return
        if (_selection.value.promotion != null) return

        val position = room.chessPosition()
        val current = _selection.value
        val promotion = promotionFor(position, current.moves, square)
        if (promotion != null) {
            _selection.value = current.copy(promotion = promotion)
            return
        }
        val move = current.moves.firstOrNull { it.to == square }
        if (move != null) {
            _selection.value = ChessSelection()
            play(position, move, me)
            return
        }

        val piece = position.pieceAt(square)
        _selection.value = if (square != current.square && piece?.color == chessColorOf(me)) {
            ChessSelection(square, ChessEngine.legalMovesFrom(position, square))
        } else {
            ChessSelection()
        }
    }

    /**
     * Plays the held promotion as [piece], checked as a legal move by the engine
     * against the room's current position. The promoted piece is part of the
     * position written to the room, so the opponent sees it too.
     */
    fun onPromotionChosen(piece: PieceType) {
        val state = uiState.value
        val promotion = _selection.value.promotion ?: return
        _selection.value = ChessSelection()
        val room = state.room ?: return
        val me = state.myPlayerNumber ?: return
        if (!state.isMyTurn || state.isResolving) return
        val position = room.chessPosition()
        val move = ChessEngine.legalMove(position, promotion.from, promotion.to, piece) ?: return
        play(position, move, me)
    }

    /** Backs out of a promotion: nothing was played, and the pawn stays where it was. */
    fun onPromotionCancelled() {
        _selection.value = ChessSelection()
    }

    private fun play(position: ChessPosition, move: ChessMove, me: Int) {
        controller.runMove {
            val after = ChessEngine.applyMove(position, move)
            val encoded = after.toRoomJson()
            when (gameEngineRepository.resolve(GameId.CHESS, GameSnapshot.Chess(after))) {
                GameOutcome.WIN -> controller.submitMove(encoded, null, RoomStatus.FINISHED, winnerPlayer = me)
                GameOutcome.DRAW -> controller.submitMove(encoded, null, RoomStatus.FINISHED)
                GameOutcome.LOSE, GameOutcome.IN_PROGRESS -> controller.submitMove(encoded, nextTurnPlayer = 3 - me)
            }
        }
    }

    override fun onCleared() = controller.onCleared()
}
