package com.example.playwin.feature.game.wordhunt

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
 * Single-player word search against a 90s countdown. Generating the grid and
 * matching a tapped start/end pair against a hidden word is local mechanics,
 * but whether that completes the puzzle (or the clock running out counts as
 * a loss) is always confirmed by [GameEngineRepository.resolve], never
 * decided directly by this ViewModel - same rule as every other game.
 */
@HiltViewModel
class WordHuntViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordHuntUiState())
    val uiState: StateFlow<WordHuntUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        startTimer()
    }

    fun onCellTap(row: Int, col: Int) {
        val state = _uiState.value
        if (state.hasWon || state.hasLost || state.isResolving) return

        val first = state.firstSelection
        if (first == null) {
            _uiState.update { it.copy(firstSelection = row to col) }
            return
        }

        val second = row to col
        if (first == second) {
            _uiState.update { it.copy(firstSelection = null) }
            return
        }

        val match = state.words.firstOrNull { placed ->
            placed.word !in state.foundWords &&
                (
                    ((placed.startRow to placed.startCol) == first && (placed.endRow to placed.endCol) == second) ||
                        ((placed.endRow to placed.endCol) == first && (placed.startRow to placed.startCol) == second)
                    )
        }

        if (match == null) {
            _uiState.update { it.copy(firstSelection = null) }
            return
        }

        val newFound = state.foundWords + match.word
        _uiState.update { it.copy(foundWords = newFound, firstSelection = null, isResolving = true) }

        viewModelScope.launch {
            val allFound = newFound.size == state.words.size
            val outcome = gameEngineRepository.resolve(
                GameId.WORD_HUNT,
                GameSnapshot.WordHunt(allWordsFound = allFound, timeExpired = false)
            )

            when (outcome) {
                GameOutcome.WIN -> {
                    timerJob?.cancel()
                    _uiState.update { it.copy(hasWon = true, isResolving = false) }
                    val coupon = rewardsRepository.claimReward(GameId.WORD_HUNT)
                    couponsRepository.addCoupon(coupon)
                    _uiState.update { it.copy(wonCouponId = coupon.id) }
                }

                GameOutcome.LOSE, GameOutcome.DRAW, GameOutcome.IN_PROGRESS -> {
                    _uiState.update { it.copy(isResolving = false) }
                }
            }
        }
    }

    fun onPlayAgain() {
        timerJob?.cancel()
        _uiState.value = WordHuntUiState()
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (secondsLeft in WORD_HUNT_TIME_SECONDS downTo 0) {
                _uiState.update { it.copy(secondsLeft = secondsLeft) }
                delay(1000)
            }

            // Time's up. If the win already landed first this coroutine would
            // have been cancelled, so reaching here always means a loss.
            _uiState.update { it.copy(isResolving = true) }
            val outcome = gameEngineRepository.resolve(
                GameId.WORD_HUNT,
                GameSnapshot.WordHunt(allWordsFound = false, timeExpired = true)
            )
            _uiState.update { it.copy(hasLost = outcome == GameOutcome.LOSE, isResolving = false) }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
    }
}
