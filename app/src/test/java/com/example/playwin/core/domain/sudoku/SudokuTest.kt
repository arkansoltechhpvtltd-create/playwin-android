package com.example.playwin.core.domain.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SudokuTest {

    /** A known-good solved grid. */
    private val solved = listOf(
        5, 3, 4, 6, 7, 8, 9, 1, 2,
        6, 7, 2, 1, 9, 5, 3, 4, 8,
        1, 9, 8, 3, 4, 2, 5, 6, 7,
        8, 5, 9, 7, 6, 1, 4, 2, 3,
        4, 2, 6, 8, 5, 3, 7, 9, 1,
        7, 1, 3, 9, 2, 4, 8, 5, 6,
        9, 6, 1, 5, 3, 7, 2, 8, 4,
        2, 8, 7, 4, 1, 9, 6, 3, 5,
        3, 4, 5, 2, 8, 6, 1, 7, 9
    )

    @Test
    fun `a generated puzzle is valid, has exactly one solution, and that solution is its own`() {
        SudokuDifficulty.entries.forEach { difficulty ->
            repeat(5) { seed ->
                val puzzle = SudokuGenerator.generate(difficulty, Random(seed))
                assertTrue(SudokuRules.isSolved(puzzle.givens, puzzle.solution))
                assertTrue(SudokuRules.conflicts(puzzle.givens).isEmpty())
                assertTrue(puzzle.givens.indices.all { puzzle.givens[it] == 0 || puzzle.givens[it] == puzzle.solution[it] })
                assertEquals(1, SudokuSolver.countSolutions(puzzle.givens))
                assertEquals(puzzle.solution, SudokuSolver.solve(puzzle.givens))
            }
        }
    }

    @Test
    fun `difficulty sets how many cells start filled`() {
        val counts = SudokuDifficulty.entries.associateWith { difficulty ->
            SudokuGenerator.generate(difficulty, Random(42)).givens.count { it != 0 }
        }
        assertEquals(SudokuDifficulty.EASY.givens, counts.getValue(SudokuDifficulty.EASY))
        assertEquals(SudokuDifficulty.MEDIUM.givens, counts.getValue(SudokuDifficulty.MEDIUM))
        // Hard may keep a few extra givens where removing any more would allow a second solution.
        assertTrue(counts.getValue(SudokuDifficulty.HARD) in SudokuDifficulty.HARD.givens..SudokuDifficulty.MEDIUM.givens)
    }

    @Test
    fun `a grid with too few clues has more than one solution`() {
        val sparse = solved.mapIndexed { i, d -> if (i < 9) d else 0 } // only the top row
        assertEquals(2, SudokuSolver.countSolutions(sparse, limit = 2))
    }

    @Test
    fun `repeats in a row, column or box are flagged`() {
        val grid = MutableList(SUDOKU_CELLS) { 0 }
        grid[0] = 5; grid[8] = 5 // same row
        grid[18] = 7; grid[72] = 7 // same column
        grid[30] = 3; grid[40] = 3 // same box (row 3 col 3, row 4 col 4)
        grid[60] = 9 // alone
        assertEquals(setOf(0, 8, 18, 72, 30, 40), SudokuRules.conflicts(grid))
    }

    @Test
    fun `a correct fill is solved, a wrong or incomplete one is not`() {
        val givens = solved.mapIndexed { i, d -> if (i % 3 == 0) d else 0 }
        assertTrue(SudokuRules.isSolved(givens, solved))

        val incomplete = solved.toMutableList().also { it[80] = 0 }
        assertFalse(SudokuRules.isSolved(givens, incomplete))

        // Swapping two cells in a row keeps the row valid but breaks their columns.
        val swapped = solved.toMutableList().also { it[1] = solved[2]; it[2] = solved[1] }
        assertFalse(SudokuRules.isSolved(givens, swapped))

        // A different valid grid that overwrites a given doesn't count either.
        val relabelled = solved.map { if (it == 1) 2 else if (it == 2) 1 else it }
        assertFalse(SudokuRules.isSolved(givens, relabelled))
        assertNotNull(SudokuSolver.solve(givens))
    }
}
