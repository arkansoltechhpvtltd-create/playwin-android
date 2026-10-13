package com.example.playwin.feature.game.eightball

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.eightball.CUE_BALL_ID
import com.example.playwin.core.domain.eightball.EightBallBot
import com.example.playwin.core.domain.eightball.EightBallPhysics
import com.example.playwin.core.domain.eightball.EightBallRules
import com.example.playwin.core.domain.eightball.EightBallShotOutcome
import com.example.playwin.core.domain.eightball.TableEvent
import com.example.playwin.core.domain.eightball.Vec2
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
 * 8-ball against the computer: the human is [EIGHT_BALL_HUMAN] and breaks,
 * [EightBallBot] plays [EIGHT_BALL_COMPUTER]. Ball physics runs frame by
 * frame from the screen's clock; groups and turn order come from
 * [EightBallRules]; the *verdict* on whether sinking the 8 won or lost
 * always comes from [GameEngineRepository.resolve], never decided locally.
 * Only a human win claims a reward.
 */
@HiltViewModel
class EightBallViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EightBallUiState())
    val uiState: StateFlow<EightBallUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var computerJob: Job? = null

    // Accumulated across every physics tick of the current shot, since a ball
    // can be pocketed, touch another or hit a cushion on any frame while the balls are rolling.
    private val pocketedThisShot = mutableListOf<Int>()
    private val eventsThisShot = mutableListOf<TableEvent>()

    init {
        startTimer()
    }

    fun onShoot(direction: Vec2, power: Float) {
        if (!_uiState.value.canAim) return
        shoot(direction, power)
    }

    /** Advances the table by one rendered frame; called from the screen's own frame clock. */
    fun onPhysicsTick(dtSeconds: Float) {
        val state = _uiState.value
        if (!state.isBallsMoving) return

        val result = EightBallPhysics.step(state.balls, dtSeconds)
        pocketedThisShot += result.newlyPocketed
        eventsThisShot += result.events

        if (result.anyMoving) {
            _uiState.update { it.copy(balls = result.balls) }
        } else {
            _uiState.update { it.copy(balls = result.balls, isBallsMoving = false, isResolving = true) }
            val outcome = EightBallRules.resolveShot(state.match, result.balls, pocketedThisShot.toList(), eventsThisShot.toList())
            resolveShot(outcome)
        }
    }

    fun onPlayAgain() {
        timerJob?.cancel()
        computerJob?.cancel()
        pocketedThisShot.clear()
        eventsThisShot.clear()
        _uiState.value = EightBallUiState()
        startTimer()
    }

    private fun shoot(direction: Vec2, power: Float) {
        val state = _uiState.value
        if (state.balls.none { it.id == CUE_BALL_ID && !it.pocketed }) return
        timerJob?.cancel()
        pocketedThisShot.clear()
        eventsThisShot.clear()
        _uiState.update {
            it.copy(
                balls = EightBallRules.launch(state.match.balls, direction, power),
                isBallsMoving = true,
                lastFoul = null,
                lastTurnTimedOut = false
            )
        }
    }

    private fun resolveShot(outcome: EightBallShotOutcome) {
        viewModelScope.launch {
            val result = gameEngineRepository.resolve(
                GameId.EIGHT_BALL,
                GameSnapshot.EightBall(
                    shooterClearedGroup = outcome.shooterClearedGroup,
                    eightBallPocketed = outcome.eightBallPocketed,
                    scratched = outcome.scratched,
                    fouled = outcome.fouled,
                    isBreakShot = outcome.wasBreak
                )
            )
            val winner = when (result) {
                GameOutcome.WIN -> outcome.shooter
                GameOutcome.LOSE -> 3 - outcome.shooter
                GameOutcome.DRAW, GameOutcome.IN_PROGRESS -> null
            }
            _uiState.update {
                it.copy(
                    match = outcome.after,
                    balls = outcome.after.balls,
                    winner = winner,
                    endedOnFoul = result == GameOutcome.LOSE,
                    lastFoul = outcome.foul,
                    isResolving = false
                )
            }

            when {
                winner == EIGHT_BALL_HUMAN -> {
                    val coupon = rewardsRepository.claimReward(GameId.EIGHT_BALL)
                    couponsRepository.addCoupon(coupon)
                    _uiState.update { it.copy(wonCouponId = coupon.id) }
                }
                winner == null -> startTurn()
                else -> Unit
            }
        }
    }

    /** Hands the next shot to whoever the match says is up: the human gets a shot clock, the computer plays. */
    private fun startTurn() {
        if (_uiState.value.currentPlayer == EIGHT_BALL_HUMAN) {
            startTimer()
            return
        }
        timerJob?.cancel()
        computerJob?.cancel()
        _uiState.update { it.copy(isComputerThinking = true, secondsLeft = EIGHT_BALL_TURN_SECONDS) }
        computerJob = viewModelScope.launch {
            val match = _uiState.value.match
            val started = System.currentTimeMillis()
            val shot = withContext(Dispatchers.Default) { EightBallBot.chooseShot(match) }
            delay((COMPUTER_MIN_THINK_MILLIS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            _uiState.update { it.copy(isComputerThinking = false) }
            shoot(shot.direction, shot.power)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (secondsLeft in EIGHT_BALL_TURN_SECONDS downTo 0) {
                _uiState.update { it.copy(secondsLeft = secondsLeft) }
                delay(1000)
            }
            // Timed out: the human's turn passes to the computer without a shot.
            _uiState.update {
                val passed = EightBallRules.passTurn(it.match)
                it.copy(match = passed, balls = passed.balls, secondsLeft = EIGHT_BALL_TURN_SECONDS, lastTurnTimedOut = true)
            }
            startTurn()
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        computerJob?.cancel()
    }
}
