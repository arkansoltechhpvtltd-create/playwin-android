package com.example.playwin.feature.game.sudoku

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.repository.RewardsRepository
import com.example.playwin.core.domain.sudoku.SudokuDifficulty
import com.example.playwin.core.domain.sudoku.SudokuGenerator
import com.example.playwin.core.domain.sudoku.SudokuRules
import com.example.playwin.core.domain.sudoku.sudokuPeers
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

/**
 * Single-player Sudoku. Puzzles come from [SudokuGenerator] (always one
 * solution); this ViewModel only records what the player enters. Whether a
 * full grid is actually solved is decided by [GameEngineRepository.resolve],
 * never here, and a win claims a reward like every other game.
 */
@HiltViewModel
class SudokuViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SudokuUiState())
    val uiState: StateFlow<SudokuUiState> = _uiState.asStateFlow()

    private var generateJob: Job? = null
    private var timerJob: Job? = null

    init {
        newGame(SudokuDifficulty.EASY)
    }

    fun onNewGame(difficulty: SudokuDifficulty) = newGame(difficulty)

    fun onSelectCell(index: Int) {
        if (_uiState.value.isGenerating || _uiState.value.hasWon) return
        _uiState.update { it.copy(selected = index) }
    }

    fun onToggleNotes() = _uiState.update { it.copy(notesMode = !it.notesMode) }

    fun onDigit(digit: Int) {
        val state = _uiState.value
        val index = state.selected ?: return
        if (!state.canEdit || state.isGiven(index) || digit !in 1..9) return

        if (state.notesMode) {
            if (state.grid[index] != 0) return
            val marks = state.notes[index].let { if (digit in it) it - digit else it + digit }
            _uiState.update { it.copy(notes = it.notes.replace(index, marks)) }
            return
        }
        if (state.grid[index] == digit) return

        val grid = state.grid.replace(index, digit)
        // Placing a digit settles its cell and rules it out of every peer's pencil marks.
        val notes = state.notes.mapIndexed { i, marks ->
            when {
                i == index -> emptySet()
                sudokuPeers(i, index) -> marks - digit
                else -> marks
            }
        }
        val madeMistake = index in SudokuRules.conflicts(grid)
        _uiState.update {
            it.copy(
                grid = grid,
                notes = notes,
                mistakes = it.mistakes + if (madeMistake) 1 else 0,
                isFilledButWrong = false
            )
        }
        if (SudokuRules.isComplete(grid)) resolve(grid)
    }

    fun onErase() {
        val state = _uiState.value
        val index = state.selected ?: return
        if (!state.canEdit || state.isGiven(index)) return
        _uiState.update {
            it.copy(grid = it.grid.replace(index, 0), notes = it.notes.replace(index, emptySet()), isFilledButWrong = false)
        }
    }

    private fun resolve(grid: List<Int>) {
        val givens = _uiState.value.puzzle?.givens ?: return
        _uiState.update { it.copy(isResolving = true) }
        viewModelScope.launch {
            when (gameEngineRepository.resolve(GameId.SUDOKU, GameSnapshot.Sudoku(givens, grid))) {
                GameOutcome.WIN -> {
                    timerJob?.cancel()
                    _uiState.update { it.copy(hasWon = true, isResolving = false, selected = null) }
                    val couponId = rewardsRepository.claimVsComputerReward(GameId.SUDOKU)
                    _uiState.update { it.copy(wonCouponId = couponId) }
                }
                GameOutcome.LOSE, GameOutcome.DRAW, GameOutcome.IN_PROGRESS ->
                    _uiState.update { it.copy(isResolving = false, isFilledButWrong = true) }
            }
        }
    }

    private fun newGame(difficulty: SudokuDifficulty) {
        generateJob?.cancel()
        timerJob?.cancel()
        _uiState.value = SudokuUiState(difficulty = difficulty)
        generateJob = viewModelScope.launch {
            val puzzle = withContext(Dispatchers.Default) { SudokuGenerator.generate(difficulty) }
            _uiState.update { it.copy(puzzle = puzzle, grid = puzzle.givens) }
            startTimer()
        }
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
        generateJob?.cancel()
        timerJob?.cancel()
    }
}

private fun <T> List<T>.replace(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
