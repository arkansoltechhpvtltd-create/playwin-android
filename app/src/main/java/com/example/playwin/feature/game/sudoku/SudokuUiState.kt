package com.example.playwin.feature.game.sudoku

import com.example.playwin.core.domain.sudoku.SUDOKU_CELLS
import com.example.playwin.core.domain.sudoku.SudokuDifficulty
import com.example.playwin.core.domain.sudoku.SudokuPuzzle
import com.example.playwin.core.domain.sudoku.SudokuRules

data class SudokuUiState(
    val difficulty: SudokuDifficulty = SudokuDifficulty.EASY,
    /** Null while a new puzzle is being generated. */
    val puzzle: SudokuPuzzle? = null,
    /** The player's grid, givens included: 81 cells row by row, 0 = empty. */
    val grid: List<Int> = List(SUDOKU_CELLS) { 0 },
    /** Pencil marks per cell; only ever on empty cells. */
    val notes: List<Set<Int>> = List(SUDOKU_CELLS) { emptySet() },
    val selected: Int? = null,
    val notesMode: Boolean = false,
    /** Entries that broke a row, column or box rule when they were made. */
    val mistakes: Int = 0,
    val elapsedSeconds: Int = 0,
    /** The grid is full but the engine didn't accept it - some cells clash. */
    val isFilledButWrong: Boolean = false,
    val hasWon: Boolean = false,
    val isResolving: Boolean = false,
    val wonCouponId: String? = null
) {
    val isGenerating: Boolean get() = puzzle == null

    /** Cells to flag in red: a digit repeated in its row, column or box. Feedback only - not the verdict. */
    val conflicts: Set<Int> get() = SudokuRules.conflicts(grid)

    fun isGiven(index: Int): Boolean = puzzle?.givens?.get(index)?.let { it != 0 } == true

    /** How many of [digit] are still to place, for the number pad. */
    fun remaining(digit: Int): Int = 9 - grid.count { it == digit }

    val canEdit: Boolean get() = !isGenerating && !hasWon && !isResolving
}
