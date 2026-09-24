package com.example.playwin.feature.game.connectfour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.connectfour.Connect4Logic
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.repository.RewardsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Two-player hotseat Connect 4. This ViewModel owns board *mechanics*
 * (dropping a piece, tracking whose turn it is, the per-turn timer) but the
 * *verdict* on whether a move won the round always comes from
 * [GameEngineRepository.resolve] - never decided locally in the UI.
 */
@HiltViewModel
class Connect4ViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(Connect4UiState())
    val uiState: StateFlow<Connect4UiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        startTimer()
    }

    fun onColumnClick(col: Int) {
        val state = _uiState.value
        if (state.winner != null || state.isDraw || state.isResolving) return

        val newBoard = Connect4Logic.placePiece(state.board, col, state.currentPlayer) ?: return

        timerJob?.cancel()
        _uiState.update { it.copy(board = newBoard, lastTurnTimedOut = false, isResolving = true) }

        viewModelScope.launch {
            val isBoardFull = Connect4Logic.isBoardFull(newBoard)
            val outcome = gameEngineRepository.resolve(
                GameId.CONNECT_FOUR,
                GameSnapshot.ConnectFour(
                    board = newBoard,
                    lastPlayer = state.currentPlayer,
                    isBoardFull = isBoardFull
                )
            )

            when (outcome) {
                GameOutcome.WIN -> {
                    _uiState.update { it.copy(winner = state.currentPlayer, isResolving = false) }
                    val coupon = rewardsRepository.claimReward(GameId.CONNECT_FOUR)
                    couponsRepository.addCoupon(coupon)
                    _uiState.update { it.copy(wonCouponId = coupon.id) }
                }

                GameOutcome.DRAW -> {
                    _uiState.update { it.copy(isDraw = true, isResolving = false) }
                }

                GameOutcome.LOSE, GameOutcome.IN_PROGRESS -> {
                    _uiState.update {
                        it.copy(
                            currentPlayer = if (state.currentPlayer == 1) 2 else 1,
                            secondsLeft = CONNECT4_TURN_SECONDS,
                            isResolving = false
                        )
                    }
                    startTimer()
                }
            }
        }
    }

    fun onPlayAgain() {
        timerJob?.cancel()
        _uiState.value = Connect4UiState()
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (secondsLeft in CONNECT4_TURN_SECONDS downTo 0) {
                _uiState.update { it.copy(secondsLeft = secondsLeft) }
                delay(1000)
            }
            // Timed out: pass the turn without placing a piece.
            _uiState.update {
                it.copy(
                    currentPlayer = if (it.currentPlayer == 1) 2 else 1,
                    secondsLeft = CONNECT4_TURN_SECONDS,
                    lastTurnTimedOut = true
                )
            }
            startTimer()
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
    }
}
