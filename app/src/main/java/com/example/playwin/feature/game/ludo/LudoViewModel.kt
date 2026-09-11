package com.example.playwin.feature.game.ludo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.ludo.LudoEngine
import com.example.playwin.core.domain.ludo.Token
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
 * Four-player hotseat Ludo. Dice rolls, token movement, captures, and the
 * 6-again rule are all delegated to the pure [LudoEngine] - this ViewModel
 * only tracks whose turn it is and the per-turn timer. The *verdict* on
 * whether a move actually won the game is still confirmed by
 * [GameEngineRepository.resolve], never decided directly here, exactly like
 * every other game.
 */
@HiltViewModel
class LudoViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LudoUiState())
    val uiState: StateFlow<LudoUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        startTimer()
    }

    fun onRollDice() {
        val state = _uiState.value
        if (!state.canRoll) return

        timerJob?.cancel()
        val rolled = LudoEngine.rollDice(state.game)
        val movable = LudoEngine.movableTokens(rolled)

        if (movable.isEmpty()) {
            _uiState.update { it.copy(game = rolled, movableTokens = emptyList(), isResolving = true) }
            viewModelScope.launch {
                delay(400) // let the rolled value render briefly before the turn moves on
                val passed = LudoEngine.passTurn(rolled)
                _uiState.update {
                    it.copy(
                        game = passed,
                        movableTokens = emptyList(),
                        isResolving = false,
                        secondsLeft = LUDO_TURN_SECONDS,
                        lastTurnTimedOut = false
                    )
                }
                startTimer()
            }
        } else {
            _uiState.update { it.copy(game = rolled, movableTokens = movable) }
        }
    }

    fun onTokenTap(token: Token) {
        val state = _uiState.value
        if (state.winner != null || state.isResolving) return
        if (state.movableTokens.none { it.color == token.color && it.index == token.index }) return

        timerJob?.cancel()
        val mover = token.color
        val newGame = LudoEngine.applyMove(state.game, token.color, token.index)

        _uiState.update { it.copy(game = newGame, movableTokens = emptyList(), isResolving = true) }

        viewModelScope.launch {
            val moverPositions = newGame.tokens.filter { it.color == mover }.map { it.position }
            val outcome = gameEngineRepository.resolve(GameId.LUDO, GameSnapshot.Ludo(moverPositions))

            when (outcome) {
                GameOutcome.WIN -> {
                    _uiState.update { it.copy(winner = mover, isResolving = false) }
                    val coupon = rewardsRepository.claimReward(GameId.LUDO)
                    couponsRepository.addCoupon(coupon)
                    _uiState.update { it.copy(wonCouponId = coupon.id) }
                }

                GameOutcome.LOSE, GameOutcome.DRAW, GameOutcome.IN_PROGRESS -> {
                    _uiState.update {
                        it.copy(isResolving = false, secondsLeft = LUDO_TURN_SECONDS, lastTurnTimedOut = false)
                    }
                    startTimer()
                }
            }
        }
    }

    fun onPlayAgain() {
        timerJob?.cancel()
        _uiState.value = LudoUiState()
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (secondsLeft in LUDO_TURN_SECONDS downTo 0) {
                _uiState.update { it.copy(secondsLeft = secondsLeft) }
                delay(1000)
            }
            // Timed out: force the turn to the next player without a roll or move.
            val forced = LudoEngine.forcePassTurn(_uiState.value.game)
            _uiState.update {
                it.copy(
                    game = forced,
                    movableTokens = emptyList(),
                    secondsLeft = LUDO_TURN_SECONDS,
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
