package com.example.playwin.feature.game.snakesandladders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.repository.RewardsRepository
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersRules
import com.example.playwin.feature.game.FirstTurn
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
 * Snakes and Ladders against the computer: the human is [SL_HUMAN], with the
 * token colour they chose, and whoever they picked rolls first; the computer
 * just rolls on its turn - there are no choices to make. The die is thrown by [GameEngineRepository.rollDie], the move is
 * [SnakesAndLaddersRules.play], and whether it won is confirmed by
 * [GameEngineRepository.resolve], never decided here. Only a human win
 * claims a reward.
 */
@HiltViewModel
class SnakesAndLaddersViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SnakesAndLaddersUiState())
    val uiState: StateFlow<SnakesAndLaddersUiState> = _uiState.asStateFlow()

    private var turnJob: Job? = null

    fun onRoll() {
        if (!_uiState.value.canRoll) return
        takeTurn()
    }

    /** Picks the player's token colour; only before the game starts. */
    fun onColorChosen(color: SlTokenColor) {
        if (_uiState.value.started) return
        _uiState.update { it.copy(humanColor = color) }
    }

    /** Picks who rolls first; only before the game starts. */
    fun onFirstTurnChosen(firstTurn: FirstTurn) {
        if (_uiState.value.started) return
        _uiState.update { it.copy(firstTurn = firstTurn, game = snakesAndLaddersGameFor(firstTurn)) }
    }

    /** Starts the game: if the computer rolls first, it rolls straight away. */
    fun onStart() {
        val state = _uiState.value
        if (state.started) return
        _uiState.update { it.copy(started = true) }
        if (state.game.turn == SL_COMPUTER) takeTurn()
    }

    fun onPlayAgain() {
        turnJob?.cancel()
        // Back to the setup, keeping the colour and first roller the player last chose.
        val last = _uiState.value
        _uiState.value = SnakesAndLaddersUiState(humanColor = last.humanColor, firstTurn = last.firstTurn)
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
                    val couponId = rewardsRepository.claimVsComputerReward(GameId.SNAKES_AND_LADDERS)
                    _uiState.update { it.copy(wonCouponId = couponId) }
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
