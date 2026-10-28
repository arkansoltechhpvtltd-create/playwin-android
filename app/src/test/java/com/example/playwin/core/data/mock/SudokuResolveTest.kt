package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.sudoku.SudokuDifficulty
import com.example.playwin.core.domain.sudoku.SudokuGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** The engine, not the UI, decides that a Sudoku is solved. */
class SudokuResolveTest {

    private val engine = MockGameEngineRepository()
    private val puzzle = SudokuGenerator.generate(SudokuDifficulty.MEDIUM, Random(3))

    private fun resolve(grid: List<Int>) = runBlocking { engine.resolve(GameId.SUDOKU, GameSnapshot.Sudoku(puzzle.givens, grid)) }

    @Test
    fun `the solution wins`() {
        assertEquals(GameOutcome.WIN, resolve(puzzle.solution))
    }

    @Test
    fun `an unfinished grid is still in progress`() {
        assertEquals(GameOutcome.IN_PROGRESS, resolve(puzzle.givens))
        val oneLeft = puzzle.solution.toMutableList().also { it[puzzle.givens.indexOf(0)] = 0 }
        assertEquals(GameOutcome.IN_PROGRESS, resolve(oneLeft))
    }

    @Test
    fun `a full grid with a wrong digit is not a win`() {
        val blank = puzzle.givens.indexOf(0)
        val wrong = puzzle.solution.toMutableList().also { it[blank] = it[blank] % 9 + 1 }
        assertEquals(GameOutcome.IN_PROGRESS, resolve(wrong))
    }

    @Test
    fun `givens that are no real puzzle cannot win`() {
        // Blank the puzzle out and "solve" it any valid way: the fill is legal, but no
        // dealt puzzle has more than one solution, so these givens were made up.
        val noGivens = List(81) { 0 }
        assertEquals(GameOutcome.LOSE, runBlocking { engine.resolve(GameId.SUDOKU, GameSnapshot.Sudoku(noGivens, puzzle.solution)) })
        val tooFew = puzzle.givens.mapIndexed { i, d -> if (i % 3 == 0) 0 else d }
        assertEquals(GameOutcome.LOSE, runBlocking { engine.resolve(GameId.SUDOKU, GameSnapshot.Sudoku(tooFew, puzzle.solution)) })
    }

    @Test
    fun `every difficulty deals a unique-solution puzzle that its solution wins`() {
        SudokuDifficulty.entries.forEach { difficulty ->
            val dealt = SudokuGenerator.generate(difficulty, Random(11))
            assertEquals(GameOutcome.WIN, runBlocking { engine.resolve(GameId.SUDOKU, GameSnapshot.Sudoku(dealt.givens, dealt.solution)) })
        }
    }

    @Test
    fun `a claimed fill that ignores the givens is not a win`() {
        val given = puzzle.givens.indexOfFirst { it != 0 }
        val tampered = puzzle.solution.toMutableList().also { it[given] = it[given] % 9 + 1 }
        assertEquals(GameOutcome.IN_PROGRESS, resolve(tampered))
    }
}
