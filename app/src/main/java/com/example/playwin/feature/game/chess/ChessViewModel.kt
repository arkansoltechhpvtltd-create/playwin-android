package com.example.playwin.feature.game.chess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.chess.ChessEngine
import com.example.playwin.core.domain.chess.ChessMove
import com.example.playwin.core.domain.chess.PieceColor
import com.example.playwin.core.domain.chess.Square
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.repository.RewardsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Two-player hotseat chess. Legal move generation and check/checkmate/
 * stalemate detection are all delegated to the pure [ChessEngine] - this
 * ViewModel only tracks selection state and whose turn it is. The *verdict*
 * on whether a checkmate actually ends the round in a win is still confirmed
 * by [GameEngineRepository.resolve], never decided directly here, exactly
 * like every other game.
 */
@HiltViewModel
class ChessViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChessUiState())
    val uiState: StateFlow<ChessUiState> = _uiState.asStateFlow()

    fun onSquareTap(square: Square) {
        val state = _uiState.value
        if (state.winner != null || state.isDraw || state.isResolving) return

        val selected = state.selectedSquare
        if (selected == null) {
            selectIfOwnPiece(state, square)
            return
        }

        if (square == selected) {
            _uiState.update { it.copy(selectedSquare = null, pendingMoves = emptyList()) }
            return
        }

        val chosenMove = state.pendingMoves.firstOrNull { it.to == square }
        if (chosenMove != null) {
            playMove(state, chosenMove)
            return
        }

        // Tapping a different own piece re-targets the selection instead of requiring a deselect first.
        selectIfOwnPiece(state, square)
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

    private fun playMove(state: ChessUiState, move: ChessMove) {
        val mover = state.position.sideToMove
        val newPosition = ChessEngine.applyMove(state.position, move)

        _uiState.update {
            it.copy(
                position = newPosition,
                selectedSquare = null,
                pendingMoves = emptyList(),
                isResolving = true
            )
        }

        viewModelScope.launch {
            val outcome = gameEngineRepository.resolve(GameId.CHESS, GameSnapshot.Chess(newPosition))

            when (outcome) {
                GameOutcome.WIN -> {
                    val winner = if (mover == PieceColor.WHITE) 1 else 2
                    _uiState.update { it.copy(winner = winner, isResolving = false) }
                    val coupon = rewardsRepository.claimReward(GameId.CHESS)
                    couponsRepository.addCoupon(coupon)
                    _uiState.update { it.copy(wonCouponId = coupon.id) }
                }

                GameOutcome.DRAW -> {
                    _uiState.update { it.copy(isDraw = true, isResolving = false) }
                }

                GameOutcome.LOSE, GameOutcome.IN_PROGRESS -> {
                    _uiState.update { it.copy(isResolving = false) }
                }
            }
        }
    }

    fun onPlayAgain() {
        _uiState.value = ChessUiState()
    }
}
