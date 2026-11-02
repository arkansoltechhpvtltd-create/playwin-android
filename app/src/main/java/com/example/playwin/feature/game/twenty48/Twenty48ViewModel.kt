package com.example.playwin.feature.game.twenty48

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
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
 * 4x4 2048. Sliding/merging tiles is local board mechanics; whether reaching
 * the 2048 tile actually counts as a win is always confirmed by
 * [GameEngineRepository.resolve], never decided directly by this ViewModel.
 */
@HiltViewModel
class Twenty48ViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        Twenty48UiState(board = Twenty48Board.emptyBoardWithStartingTiles())
    )
    val uiState: StateFlow<Twenty48UiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        startTimer()
    }

    fun onSwipe(direction: SwipeDirection) {
        val state = _uiState.value
        if (state.hasWon || state.isGameOver || state.isResolving) return

        val (movedBoard, moved) = Twenty48Board.move(state.board, direction)
        if (!moved) return

        val newBoard = Twenty48Board.spawnRandomTile(movedBoard)
        _uiState.update { it.copy(board = newBoard, isResolving = true) }

        viewModelScope.launch {
            val outcome = gameEngineRepository.resolve(
                GameId.TWENTY_FORTY_EIGHT,
                GameSnapshot.TwentyFortyEight(board = newBoard)
            )

            when (outcome) {
                GameOutcome.WIN -> {
                    timerJob?.cancel()
                    _uiState.update { it.copy(hasWon = true, isResolving = false) }
                    val couponId = rewardsRepository.claimVsComputerReward(GameId.TWENTY_FORTY_EIGHT)
                    _uiState.update { it.copy(wonCouponId = couponId) }
                }

                GameOutcome.LOSE -> {
                    timerJob?.cancel()
                    _uiState.update { it.copy(isGameOver = true, isResolving = false) }
                }

                GameOutcome.DRAW, GameOutcome.IN_PROGRESS -> {
                    _uiState.update { it.copy(isResolving = false) }
                }
            }
        }
    }

    fun onPlayAgain() {
        timerJob?.cancel()
        _uiState.value = Twenty48UiState(board = Twenty48Board.emptyBoardWithStartingTiles())
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
    }
}
