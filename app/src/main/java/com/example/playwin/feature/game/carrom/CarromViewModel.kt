package com.example.playwin.feature.game.carrom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.carrom.CarromBot
import com.example.playwin.core.domain.carrom.CarromPhysics
import com.example.playwin.core.domain.carrom.CarromRules
import com.example.playwin.core.domain.carrom.CarromShotOutcome
import com.example.playwin.core.domain.carrom.QueenEvent
import com.example.playwin.core.domain.carrom.STRIKER_ID
import com.example.playwin.core.domain.carrom.Vec2
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

private const val COMPUTER_MIN_THINK_MILLIS = 700L

/**
 * Carrom against the computer: the human is [CARROM_HUMAN] (bottom), and
 * [CarromBot] plays [CARROM_COMPUTER] (top). Striker physics runs frame by
 * frame from the screen's clock; scoring and turn order come from
 * [CarromRules]; the *verdict* on who wins once the board is cleared always
 * comes from [GameEngineRepository.resolve], never decided locally. Only a
 * human win claims a reward.
 */
@HiltViewModel
class CarromViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CarromUiState())
    val uiState: StateFlow<CarromUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var computerJob: Job? = null

    // Accumulated across every physics tick of the current shot, since a coin
    // can be pocketed on any frame while the pieces are rolling.
    private val pocketedThisShot = mutableListOf<Int>()

    init {
        startTimer()
    }

    fun onShoot(direction: Vec2, power: Float) {
        if (!_uiState.value.canAim) return
        shoot(direction, power)
    }

    /**
     * Slides the striker along the shooter's baseline to [x] before the shot -
     * only sideways, clamped between the baseline's end circles. Ignored once
     * the shot is taken, and it won't push the striker onto a coin.
     */
    fun onPlaceStriker(x: Float) {
        val state = _uiState.value
        if (!state.canAim) return
        val coins = slideStriker(state.match.coins, x) ?: return
        _uiState.update { it.copy(match = it.match.copy(coins = coins), coins = coins) }
    }

    /** Advances the board by one rendered frame; called from the screen's own frame clock. */
    fun onPhysicsTick(dtSeconds: Float) {
        val state = _uiState.value
        if (!state.isPiecesMoving) return

        val result = CarromPhysics.step(state.coins, dtSeconds)
        pocketedThisShot += result.newlyPocketed

        if (result.anyMoving) {
            _uiState.update { it.copy(coins = result.coins) }
        } else {
            _uiState.update { it.copy(coins = result.coins, isPiecesMoving = false, isResolving = true) }
            resolveShot(CarromRules.resolveShot(state.match, result.coins, pocketedThisShot.toList()))
        }
    }

    fun onPlayAgain() {
        timerJob?.cancel()
        computerJob?.cancel()
        pocketedThisShot.clear()
        _uiState.value = CarromUiState()
        startTimer()
    }

    private fun shoot(direction: Vec2, power: Float) {
        val state = _uiState.value
        if (state.coins.none { it.id == STRIKER_ID && !it.pocketed }) return
        timerJob?.cancel()
        pocketedThisShot.clear()
        _uiState.update {
            it.copy(
                coins = CarromRules.launch(state.match.coins, direction, power),
                isPiecesMoving = true,
                fouled = false,
                lastTurnTimedOut = false
            )
        }
    }

    private fun resolveShot(outcome: CarromShotOutcome) {
        val after = outcome.after
        viewModelScope.launch {
            val result = gameEngineRepository.resolve(
                GameId.CARROM,
                GameSnapshot.Carrom(
                    boardCleared = after.boardCleared,
                    shooterScore = after.scores.getValue(outcome.shooter),
                    opponentScore = after.scores.getValue(3 - outcome.shooter)
                )
            )
            val winner = when (result) {
                GameOutcome.WIN -> outcome.shooter
                GameOutcome.LOSE -> 3 - outcome.shooter
                GameOutcome.DRAW, GameOutcome.IN_PROGRESS -> null
            }
            _uiState.update {
                it.copy(
                    match = after,
                    coins = after.coins,
                    winner = winner,
                    isDraw = result == GameOutcome.DRAW,
                    fouled = outcome.fouled,
                    lastQueenEvent = outcome.queenEvent,
                    isResolving = false
                )
            }

            when {
                winner == CARROM_HUMAN -> {
                    val coupon = rewardsRepository.claimReward(GameId.CARROM)
                    couponsRepository.addCoupon(coupon)
                    _uiState.update { it.copy(wonCouponId = coupon.id) }
                }
                result == GameOutcome.IN_PROGRESS -> startTurn()
                else -> Unit
            }
        }
    }

    /** Hands the next shot to whoever the match says is up: the human gets a shot clock, the computer plays. */
    private fun startTurn() {
        if (_uiState.value.currentPlayer == CARROM_HUMAN) {
            startTimer()
            return
        }
        timerJob?.cancel()
        computerJob?.cancel()
        _uiState.update { it.copy(isComputerThinking = true, secondsLeft = CARROM_TURN_SECONDS) }
        computerJob = viewModelScope.launch {
            val match = _uiState.value.match
            val started = System.currentTimeMillis()
            val shot = withContext(Dispatchers.Default) { CarromBot.chooseShot(match) }
            delay((COMPUTER_MIN_THINK_MILLIS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            _uiState.update { it.copy(isComputerThinking = false) }
            shoot(shot.direction, shot.power)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (secondsLeft in CARROM_TURN_SECONDS downTo 0) {
                _uiState.update { it.copy(secondsLeft = secondsLeft) }
                delay(1000)
            }
            // Timed out: the human's turn passes to the computer without a shot.
            _uiState.update {
                val passed = CarromRules.passTurn(it.match)
                val forfeitedQueen = it.match.queenCoverOwedBy == it.currentPlayer
                it.copy(
                    match = passed,
                    coins = passed.coins,
                    secondsLeft = CARROM_TURN_SECONDS,
                    lastTurnTimedOut = true,
                    lastQueenEvent = QueenEvent.RETURNED.takeIf { forfeitedQueen }
                )
            }
            startTurn()
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        computerJob?.cancel()
    }
}
