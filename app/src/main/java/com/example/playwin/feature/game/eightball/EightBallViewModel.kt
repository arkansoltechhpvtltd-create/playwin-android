package com.example.playwin.feature.game.eightball

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * Two-player hotseat 8-ball. This ViewModel owns table *mechanics* - ball
 * physics, whose turn it is, group assignment, the per-turn timer - but the
 * *verdict* on whether pocketing the 8-ball actually won or lost the round
 * always comes from [GameEngineRepository.resolve], never decided locally
 * in the UI, exactly like Connect 4 and 2048.
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

    // Accumulated across every physics tick of the current shot, since a ball
    // can be pocketed on any frame while the balls are rolling.
    private val pocketedThisShot = mutableListOf<Int>()

    init {
        startTimer()
    }

    fun onShoot(direction: Vec2, power: Float) {
        val state = _uiState.value
        if (!state.canAim) return
        if (state.balls.none { it.id == 0 && !it.pocketed }) return

        timerJob?.cancel()
        pocketedThisShot.clear()

        val speed = EightBallPhysics.MAX_SHOT_SPEED * power.coerceIn(0f, 1f)
        val aimedBalls = state.balls.map {
            if (it.id == 0) it.copy(velocity = direction.normalizedOrZero() * speed) else it
        }
        _uiState.update {
            it.copy(balls = aimedBalls, isBallsMoving = true, fouled = false, lastTurnTimedOut = false)
        }
    }

    /** Advances the table by one rendered frame; called from the screen's own frame clock. */
    fun onPhysicsTick(dtSeconds: Float) {
        val state = _uiState.value
        if (!state.isBallsMoving) return

        val result = EightBallPhysics.step(state.balls, dtSeconds)
        pocketedThisShot += result.newlyPocketed

        if (result.anyMoving) {
            _uiState.update { it.copy(balls = result.balls) }
        } else {
            _uiState.update { it.copy(balls = result.balls, isBallsMoving = false, isResolving = true) }
            resolveShot(result.balls)
        }
    }

    private fun resolveShot(balls: List<Ball>) {
        val state = _uiState.value
        val potted = pocketedThisShot.toList()
        val scratched = potted.contains(0)

        var groups = state.playerGroups
        if (groups.isEmpty()) {
            val firstGroupBall = potted.firstOrNull { it != 0 && it != 8 }
            val group = firstGroupBall?.let(::ballGroupOf)
            if (group != null) {
                val opponent = if (state.currentPlayer == 1) 2 else 1
                groups = mapOf(state.currentPlayer to group, opponent to group.other())
            }
        }

        val shooterGroup = groups[state.currentPlayer]
        val eightBallPocketed = potted.contains(8)
        val shooterClearedGroup = shooterGroup != null &&
            balls.filter { ballGroupOf(it.id) == shooterGroup }.all { it.pocketed }

        // A scratched cue ball is respotted for the next shot.
        val settledBalls = if (scratched) {
            balls.map {
                if (it.id == 0) {
                    it.copy(position = Vec2(EightBallTable.CUE_START_X, EightBallTable.CENTER_Y), velocity = Vec2.Zero, pocketed = false)
                } else {
                    it
                }
            }
        } else {
            balls
        }

        viewModelScope.launch {
            val outcome = gameEngineRepository.resolve(
                GameId.EIGHT_BALL,
                GameSnapshot.EightBall(
                    shooterClearedGroup = shooterClearedGroup,
                    eightBallPocketed = eightBallPocketed,
                    scratched = scratched
                )
            )

            when (outcome) {
                GameOutcome.WIN -> {
                    _uiState.update {
                        it.copy(
                            balls = settledBalls,
                            playerGroups = groups,
                            winner = state.currentPlayer,
                            isResolving = false
                        )
                    }
                    awardCoupon()
                }

                GameOutcome.LOSE -> {
                    val winner = if (state.currentPlayer == 1) 2 else 1
                    _uiState.update {
                        it.copy(
                            balls = settledBalls,
                            playerGroups = groups,
                            winner = winner,
                            fouled = true,
                            isResolving = false
                        )
                    }
                    awardCoupon()
                }

                GameOutcome.DRAW, GameOutcome.IN_PROGRESS -> {
                    val pottedOwnBall = shooterGroup != null && potted.any { ballGroupOf(it) == shooterGroup }
                    val nextPlayer = if (!scratched && pottedOwnBall) {
                        state.currentPlayer
                    } else {
                        if (state.currentPlayer == 1) 2 else 1
                    }

                    _uiState.update {
                        it.copy(
                            balls = settledBalls,
                            playerGroups = groups,
                            currentPlayer = nextPlayer,
                            secondsLeft = EIGHT_BALL_TURN_SECONDS,
                            fouled = scratched,
                            isResolving = false
                        )
                    }
                    startTimer()
                }
            }
        }
    }

    private suspend fun awardCoupon() {
        val coupon = rewardsRepository.claimReward(GameId.EIGHT_BALL)
        couponsRepository.addCoupon(coupon)
        _uiState.update { it.copy(wonCouponId = coupon.id) }
    }

    fun onPlayAgain() {
        timerJob?.cancel()
        pocketedThisShot.clear()
        _uiState.value = EightBallUiState()
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (secondsLeft in EIGHT_BALL_TURN_SECONDS downTo 0) {
                _uiState.update { it.copy(secondsLeft = secondsLeft) }
                delay(1000)
            }
            // Timed out: pass the turn without taking a shot.
            _uiState.update {
                it.copy(
                    currentPlayer = if (it.currentPlayer == 1) 2 else 1,
                    secondsLeft = EIGHT_BALL_TURN_SECONDS,
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
