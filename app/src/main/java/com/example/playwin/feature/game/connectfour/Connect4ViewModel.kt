package com.example.playwin.feature.game.connectfour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.connectfour.Connect4Bot
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

private const val COMPUTER_THINK_MILLIS = 600L

/**
 * Connect 4 against the computer: the human is [CONNECT4_HUMAN], the
 * [Connect4Bot] is [CONNECT4_COMPUTER]. This ViewModel owns board *mechanics*
 * (dropping a piece, whose turn it is, the per-turn timer) but the *verdict*
 * on whether a move won the round always comes from
 * [GameEngineRepository.resolve] - never decided locally in the UI. Only a
 * human win claims a reward; a computer win is a loss for the player.
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
    private var computerJob: Job? = null

    init {
        startTimer()
    }

    fun onColumnClick(col: Int) {
        val state = _uiState.value
        if (state.currentPlayer != CONNECT4_HUMAN || state.isGameOver || state.isResolving) return
        playMove(col, CONNECT4_HUMAN)
    }

    fun onPlayAgain() {
        timerJob?.cancel()
        computerJob?.cancel()
        _uiState.value = Connect4UiState()
        startTimer()
    }

    private fun playMove(col: Int, player: Int) {
        val state = _uiState.value
        val newBoard = Connect4Logic.placePiece(state.board, col, player) ?: return

        timerJob?.cancel()
        _uiState.update { it.copy(board = newBoard, lastTurnTimedOut = false, isResolving = true) }

        viewModelScope.launch {
            val outcome = gameEngineRepository.resolve(
                GameId.CONNECT_FOUR,
                GameSnapshot.ConnectFour(
                    board = newBoard,
                    lastPlayer = player,
                    isBoardFull = Connect4Logic.isBoardFull(newBoard)
                )
            )

            when (outcome) {
                GameOutcome.WIN -> {
                    _uiState.update { it.copy(winner = player, isResolving = false) }
                    if (player == CONNECT4_HUMAN) {
                        val coupon = rewardsRepository.claimReward(GameId.CONNECT_FOUR)
                        couponsRepository.addCoupon(coupon)
                        _uiState.update { it.copy(wonCouponId = coupon.id) }
                    }
                }

                GameOutcome.DRAW -> {
                    _uiState.update { it.copy(isDraw = true, isResolving = false) }
                }

                GameOutcome.LOSE, GameOutcome.IN_PROGRESS -> {
                    val next = if (player == CONNECT4_HUMAN) CONNECT4_COMPUTER else CONNECT4_HUMAN
                    _uiState.update { it.copy(currentPlayer = next, isResolving = false) }
                    if (next == CONNECT4_COMPUTER) playComputerTurn() else startTimer()
                }
            }
        }
    }

    private fun playComputerTurn() {
        computerJob?.cancel()
        computerJob = viewModelScope.launch {
            delay(COMPUTER_THINK_MILLIS)
            val col = Connect4Bot.chooseColumn(_uiState.value.board, CONNECT4_COMPUTER, CONNECT4_HUMAN)
                ?: return@launch
            playMove(col, CONNECT4_COMPUTER)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (secondsLeft in CONNECT4_TURN_SECONDS downTo 0) {
                _uiState.update { it.copy(secondsLeft = secondsLeft) }
                delay(1000)
            }
            // Timed out: the human's turn passes to the computer without a piece placed.
            _uiState.update {
                it.copy(
                    currentPlayer = CONNECT4_COMPUTER,
                    secondsLeft = CONNECT4_TURN_SECONDS,
                    lastTurnTimedOut = true
                )
            }
            playComputerTurn()
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        computerJob?.cancel()
    }
}
