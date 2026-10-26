package com.example.playwin.feature.game.snakesandladders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.repository.RewardsRepository
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A beat between the token coming to rest and the computer's own roll, so turns read one at a time. */
private const val COMPUTER_PAUSE_MILLIS = 450L

/**
 * Snakes and Ladders against the computer: the human is [SL_HUMAN] and rolls
 * first, and the computer just rolls on its turn - there are no choices to
 * make. The die is thrown by [GameEngineRepository.rollDie], the move is
 * [SnakesAndLaddersRules.play], and whether it won is confirmed by
 * [GameEngineRepository.resolve], never decided here. Only a human win
 * claims a reward.
 */
@HiltViewModel
class SnakesAndLaddersViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SnakesAndLaddersUiState())
    val uiState: StateFlow<SnakesAndLaddersUiState> = _uiState.asStateFlow()

    private var turnJob: Job? = null

    fun onRoll() {
        if (!_uiState.value.canRoll) return
        takeTurn()
    }

    fun onPlayAgain() {
        turnJob?.cancel()
        _uiState.value = SnakesAndLaddersUiState()
    }

    private fun takeTurn() {
        _uiState.update { it.copy(isBusy = true) }
        turnJob = viewModelScope.launch {
            val before = _uiState.value.game
            val roll = gameEngineRepository.rollDie(GameId.SNAKES_AND_LADDERS)
            val turn = SnakesAndLaddersRules.play(before, roll)
            val outcome = gameEngineRepository.resolve(GameId.SNAKES_AND_LADDERS, GameSnapshot.SnakesAndLadders(before, roll))

            _uiState.update { it.copy(game = turn.after, lastMove = turn.move) }
            // Let the die land and the token walk (and climb or slide) before anything else happens.
            delay(SnakesAndLaddersTiming.moveMillis(turn.move))

            if (outcome == GameOutcome.WIN) {
                val mover = turn.move.player
                _uiState.update { it.copy(winner = mover, isBusy = false) }
                if (mover == SL_HUMAN) {
                    val coupon = rewardsRepository.claimReward(GameId.SNAKES_AND_LADDERS)
                    couponsRepository.addCoupon(coupon)
                    _uiState.update { it.copy(wonCouponId = coupon.id) }
                }
                return@launch
            }

            _uiState.update { it.copy(isBusy = false) }
            if (turn.after.turn == SL_COMPUTER) {
                delay(COMPUTER_PAUSE_MILLIS)
                takeTurn()
            }
        }
    }

    override fun onCleared() {
        turnJob?.cancel()
    }
}
