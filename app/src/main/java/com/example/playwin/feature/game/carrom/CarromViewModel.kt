package com.example.playwin.feature.game.carrom

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
 * Two-player hotseat carrom. This ViewModel owns board *mechanics* - striker
 * physics, whose turn it is, color assignment, scoring, the per-turn timer -
 * but the *verdict* on who wins once the board is cleared always comes from
 * [GameEngineRepository.resolve], never decided locally in the UI, exactly
 * like Connect 4, 2048, and 8-Ball.
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

    // Accumulated across every physics tick of the current shot, since a coin
    // can be pocketed on any frame while the pieces are rolling.
    private val pocketedThisShot = mutableListOf<Int>()

    init {
        startTimer()
    }

    fun onShoot(direction: Vec2, power: Float) {
        val state = _uiState.value
        if (!state.canAim) return
        if (state.coins.none { it.id == STRIKER_ID && !it.pocketed }) return

        timerJob?.cancel()
        pocketedThisShot.clear()

        val speed = CarromPhysics.MAX_SHOT_SPEED * power.coerceIn(0f, 1f)
        val aimedCoins = state.coins.map {
            if (it.id == STRIKER_ID) it.copy(velocity = direction.normalizedOrZero() * speed) else it
        }
        _uiState.update {
            it.copy(coins = aimedCoins, isPiecesMoving = true, fouled = false, lastTurnTimedOut = false)
        }
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
            resolveShot(result.coins)
        }
    }

    private fun resolveShot(coins: List<Coin>) {
        val state = _uiState.value
        val potted = pocketedThisShot.toList()
        val scratched = potted.contains(STRIKER_ID)
        val pottedCoins = potted.filter { it != STRIKER_ID }

        var colors = state.playerColors
        if (colors.isEmpty()) {
            val firstColoredId = pottedCoins.firstOrNull { coinColorOf(it) != CoinColor.QUEEN }
            val color = firstColoredId?.let(::coinColorOf)
            if (color != null) {
                val opponent = if (state.currentPlayer == 1) 2 else 1
                colors = mapOf(state.currentPlayer to color, opponent to color.other())
            }
        }

        var pointsGained = 0
        if (!scratched) {
            val shooterColor = colors[state.currentPlayer]
            pottedCoins.forEach { id ->
                pointsGained += when (coinColorOf(id)) {
                    CoinColor.QUEEN -> 3
                    shooterColor -> 1
                    else -> 0
                }
            }
        }

        val opponent = if (state.currentPlayer == 1) 2 else 1
        val newScores = state.scores + (state.currentPlayer to (state.scores.getValue(state.currentPlayer) + pointsGained))
        val boardCleared = coins.filter { it.id != STRIKER_ID }.all { it.pocketed }

        // A foul (scratch) always passes the turn; otherwise potting your own
        // color or the queen earns another shot, same as real carrom rules.
        val nextPlayer = if (!scratched && pointsGained > 0) state.currentPlayer else opponent
        val respottedCoins = coins.map {
            if (it.id == STRIKER_ID) it.copy(position = CarromBoard.baselineFor(nextPlayer), velocity = Vec2.Zero, pocketed = false) else it
        }

        viewModelScope.launch {
            val outcome = gameEngineRepository.resolve(
                GameId.CARROM,
                GameSnapshot.Carrom(
                    boardCleared = boardCleared,
                    shooterScore = newScores.getValue(state.currentPlayer),
                    opponentScore = newScores.getValue(opponent)
                )
            )

            when (outcome) {
                GameOutcome.WIN -> {
                    _uiState.update {
                        it.copy(
                            coins = respottedCoins,
                            playerColors = colors,
                            scores = newScores,
                            winner = state.currentPlayer,
                            isResolving = false
                        )
                    }
                    awardCoupon()
                }

                GameOutcome.LOSE -> {
                    _uiState.update {
                        it.copy(
                            coins = respottedCoins,
                            playerColors = colors,
                            scores = newScores,
                            winner = opponent,
                            isResolving = false
                        )
                    }
                    awardCoupon()
                }

                GameOutcome.DRAW -> {
                    _uiState.update {
                        it.copy(
                            coins = respottedCoins,
                            playerColors = colors,
                            scores = newScores,
                            isDraw = true,
                            isResolving = false
                        )
                    }
                }

                GameOutcome.IN_PROGRESS -> {
                    _uiState.update {
                        it.copy(
                            coins = respottedCoins,
                            playerColors = colors,
                            scores = newScores,
                            currentPlayer = nextPlayer,
                            secondsLeft = CARROM_TURN_SECONDS,
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
        val coupon = rewardsRepository.claimReward(GameId.CARROM)
        couponsRepository.addCoupon(coupon)
        _uiState.update { it.copy(wonCouponId = coupon.id) }
    }

    fun onPlayAgain() {
        timerJob?.cancel()
        pocketedThisShot.clear()
        _uiState.value = CarromUiState()
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (secondsLeft in CARROM_TURN_SECONDS downTo 0) {
                _uiState.update { it.copy(secondsLeft = secondsLeft) }
                delay(1000)
            }
            // Timed out: pass the turn without taking a shot.
            _uiState.update { state ->
                val nextPlayer = if (state.currentPlayer == 1) 2 else 1
                state.copy(
                    coins = state.coins.map {
                        if (it.id == STRIKER_ID) it.copy(position = CarromBoard.baselineFor(nextPlayer), velocity = Vec2.Zero, pocketed = false) else it
                    },
                    currentPlayer = nextPlayer,
                    secondsLeft = CARROM_TURN_SECONDS,
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
