package com.example.playwin.feature.game.ludo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.ludo.LudoBot
import com.example.playwin.core.domain.ludo.LudoEngine
import com.example.playwin.core.domain.ludo.LudoState
import com.example.playwin.core.domain.ludo.Token
import com.example.playwin.core.domain.ludo.currentColor
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

private const val COMPUTER_STEP_MILLIS = 700L

/**
 * 1v1 Ludo against the computer: the human is [LUDO_HUMAN] (Red, moves
 * first), [LudoBot] plays [LUDO_COMPUTER] (Yellow). Dice rolls, token
 * movement, captures, and the extra-roll rules are all delegated to the pure
 * [LudoEngine]; the *verdict* on whether a move won the game is still
 * confirmed by [GameEngineRepository.resolve], never decided directly here.
 * Only a human win claims a reward.
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
    private var computerJob: Job? = null

    init {
        startTimer()
    }

    fun onRollDice() {
        val state = _uiState.value
        if (!state.canRoll) return
        timerJob?.cancel()
        roll(state.game)
    }

    fun onTokenTap(token: Token) {
        val state = _uiState.value
        if (state.winner != null || state.isResolving || state.game.currentColor != LUDO_HUMAN) return
        if (state.movableTokens.none { it.color == token.color && it.index == token.index }) return
        timerJob?.cancel()
        move(state.game, token)
    }

    fun onPlayAgain() {
        timerJob?.cancel()
        computerJob?.cancel()
        _uiState.value = LudoUiState()
        startTimer()
    }

    /** Throws the die - always the game engine's, never one picked here - and plays out what it allows. */
    private fun roll(game: LudoState) {
        // Busy while the engine throws, so a second tap can't roll twice.
        _uiState.update { it.copy(isResolving = true) }
        viewModelScope.launch {
            val rolled = LudoEngine.rollDice(game, gameEngineRepository.rollDie(GameId.LUDO))
            val movable = LudoEngine.movableTokens(rolled)

            if (movable.isEmpty()) {
                _uiState.update { it.copy(game = rolled, movableTokens = emptyList(), isResolving = true) }
                delay(400) // let the rolled value render briefly before the turn moves on
                val passed = LudoEngine.passTurn(rolled)
                val forfeited = LudoEngine.isTurnForfeited(rolled)
                _uiState.update {
                    it.copy(game = passed, isResolving = false, lastTurnTimedOut = false, lastTurnForfeited = forfeited)
                }
                startTurn(passed)
            } else if (rolled.currentColor == LUDO_HUMAN) {
                _uiState.update { it.copy(game = rolled, movableTokens = movable, isResolving = false) }
            } else {
                _uiState.update { it.copy(game = rolled, movableTokens = emptyList(), isResolving = false) }
                computerJob = viewModelScope.launch {
                    delay(COMPUTER_STEP_MILLIS)
                    LudoBot.chooseToken(rolled)?.let { move(rolled, it) }
                }
            }
        }
    }

    private fun move(game: LudoState, token: Token) {
        val mover = token.color
        val newGame = LudoEngine.applyMove(game, token.color, token.index)
        _uiState.update { it.copy(game = newGame, movableTokens = emptyList(), isResolving = true) }

        viewModelScope.launch {
            val moverPositions = newGame.tokens.filter { it.color == mover }.map { it.position }
            val outcome = gameEngineRepository.resolve(GameId.LUDO, GameSnapshot.Ludo(moverPositions))

            when (outcome) {
                GameOutcome.WIN -> {
                    _uiState.update { it.copy(winner = mover, isResolving = false) }
                    if (mover == LUDO_HUMAN) {
                        val coupon = rewardsRepository.claimReward(GameId.LUDO)
                        couponsRepository.addCoupon(coupon)
                        _uiState.update { it.copy(wonCouponId = coupon.id) }
                    }
                }

                GameOutcome.LOSE, GameOutcome.DRAW, GameOutcome.IN_PROGRESS -> {
                    _uiState.update { it.copy(isResolving = false, lastTurnTimedOut = false, lastTurnForfeited = false) }
                    startTurn(newGame)
                }
            }
        }
    }

    /** Hands the next turn to whoever [game] says is up: the human gets a timer, the computer just plays. */
    private fun startTurn(game: LudoState) {
        if (game.currentColor == LUDO_HUMAN) {
            startTimer()
        } else {
            timerJob?.cancel()
            computerJob?.cancel()
            computerJob = viewModelScope.launch {
                delay(COMPUTER_STEP_MILLIS)
                roll(game)
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (secondsLeft in LUDO_TURN_SECONDS downTo 0) {
                _uiState.update { it.copy(secondsLeft = secondsLeft) }
                delay(1000)
            }
            // Timed out: the human's turn passes to the computer without a roll or move.
            val forced = LudoEngine.forcePassTurn(_uiState.value.game)
            _uiState.update {
                it.copy(
                    game = forced,
                    movableTokens = emptyList(),
                    secondsLeft = LUDO_TURN_SECONDS,
                    lastTurnTimedOut = true,
                    lastTurnForfeited = false
                )
            }
            startTurn(forced)
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        computerJob?.cancel()
    }
}
