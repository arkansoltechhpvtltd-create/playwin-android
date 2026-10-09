package com.example.playwin.feature.game.chess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.chess.ChessBot
import com.example.playwin.core.domain.chess.ChessEngine
import com.example.playwin.core.domain.chess.ChessMove
import com.example.playwin.core.domain.chess.PieceColor
import com.example.playwin.core.domain.chess.PieceType
import com.example.playwin.core.domain.chess.Square
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.repository.RewardsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val COMPUTER_THINK_MILLIS = 500L

/**
 * Chess against the computer: the human plays White ([CHESS_HUMAN]),
 * [ChessBot] plays Black. Legal move generation and check/checkmate/
 * stalemate detection are all delegated to the pure [ChessEngine] - this
 * ViewModel only tracks selection state and whose turn it is. The *verdict*
 * on whether a checkmate actually ends the round in a win is still confirmed
 * by [GameEngineRepository.resolve], never decided directly here. Only a
 * human win claims a reward.
 */
@HiltViewModel
class ChessViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChessUiState())
    val uiState: StateFlow<ChessUiState> = _uiState.asStateFlow()

    private var computerJob: Job? = null

    fun onSquareTap(square: Square) {
        val state = _uiState.value
        if (state.isGameOver || state.isResolving || state.isComputerThinking || !state.isHumanTurn) return
        if (state.promotion != null) return

        val selected = state.selectedSquare
        if (selected == null) {
            selectIfOwnPiece(state, square)
            return
        }

        if (square == selected) {
            _uiState.update { it.copy(selectedSquare = null, pendingMoves = emptyList()) }
            return
        }

        // A pawn onto the last rank waits for the player to pick what it becomes.
        val promotion = promotionFor(state.position, state.pendingMoves, square)
        if (promotion != null) {
            _uiState.update { it.copy(promotion = promotion) }
            return
        }

        val chosenMove = state.pendingMoves.firstOrNull { it.to == square }
        if (chosenMove != null) {
            playMove(chosenMove)
            return
        }

        // Tapping a different own piece re-targets the selection instead of requiring a deselect first.
        selectIfOwnPiece(state, square)
    }

    /** Plays the held promotion as [piece]. The engine checks it's a legal move before it's played. */
    fun onPromotionChosen(piece: PieceType) {
        val state = _uiState.value
        val promotion = state.promotion ?: return
        val move = ChessEngine.legalMove(state.position, promotion.from, promotion.to, piece)
        if (move == null || !state.isHumanTurn || state.isGameOver) {
            onPromotionCancelled()
            return
        }
        _uiState.update { it.copy(promotion = null) }
        playMove(move)
    }

    /** Backs out of a promotion: nothing was played, and the pawn stays where it was. */
    fun onPromotionCancelled() {
        _uiState.update { it.copy(promotion = null, selectedSquare = null, pendingMoves = emptyList()) }
    }

    fun onPlayAgain() {
        computerJob?.cancel()
        _uiState.value = ChessUiState()
    }

    private fun selectIfOwnPiece(state: ChessUiState, square: Square) {
        val piece = state.position.pieceAt(square)
        if (piece == null || piece.color != state.position.sideToMove) {
            _uiState.update { it.copy(selectedSquare = null, pendingMoves = emptyList()) }
            return
        }
        val moves = ChessEngine.legalMovesFrom(state.position, square)
        _uiState.update { it.copy(selectedSquare = square, pendingMoves = moves) }
    }

    private fun playMove(move: ChessMove) {
        val position = _uiState.value.position
        val mover = position.sideToMove
        val newPosition = ChessEngine.applyMove(position, move)

        _uiState.update {
            it.copy(position = newPosition, selectedSquare = null, pendingMoves = emptyList(), isResolving = true)
        }

        viewModelScope.launch {
            when (gameEngineRepository.resolve(GameId.CHESS, GameSnapshot.Chess(newPosition))) {
                GameOutcome.WIN -> {
                    _uiState.update { it.copy(winner = if (mover == PieceColor.WHITE) 1 else 2, isResolving = false) }
                    if (mover == CHESS_HUMAN) {
                        val coupon = rewardsRepository.claimReward(GameId.CHESS)
                        couponsRepository.addCoupon(coupon)
                        _uiState.update { it.copy(wonCouponId = coupon.id) }
                    }
                }

                GameOutcome.DRAW -> _uiState.update { it.copy(isDraw = true, isResolving = false) }

                GameOutcome.LOSE, GameOutcome.IN_PROGRESS -> {
                    _uiState.update { it.copy(isResolving = false) }
                    if (newPosition.sideToMove != CHESS_HUMAN) playComputerMove()
                }
            }
        }
    }

    private fun playComputerMove() {
        computerJob?.cancel()
        _uiState.update { it.copy(isComputerThinking = true) }
        computerJob = viewModelScope.launch {
            delay(COMPUTER_THINK_MILLIS)
            val move = withContext(Dispatchers.Default) { ChessBot.chooseMove(_uiState.value.position) }
            _uiState.update { it.copy(isComputerThinking = false) }
            move?.let(::playMove)
        }
    }

    override fun onCleared() {
        computerJob?.cancel()
    }
}
