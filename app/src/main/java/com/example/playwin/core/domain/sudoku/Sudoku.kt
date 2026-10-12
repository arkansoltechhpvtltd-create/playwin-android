package com.example.playwin.core.domain.sudoku

import kotlin.random.Random

/**
 * Pure Sudoku logic - no Android or Compose. A grid is 81 cells in row-major
 * order (index = row * 9 + col), each 1..9, or 0 for empty.
 */
const val SUDOKU_SIZE = 9
const val SUDOKU_CELLS = SUDOKU_SIZE * SUDOKU_SIZE
const val SUDOKU_BOX = 3

/** How many cells a new puzzle starts with filled in. */
enum class SudokuDifficulty(val label: String, val givens: Int) {
    EASY("Easy", 40),
    MEDIUM("Medium", 32),
    HARD("Hard", 26)
}

/** A puzzle: the starting [givens] (0 = empty) and the one [solution] they lead to. */
data class SudokuPuzzle(
    val givens: List<Int>,
    val solution: List<Int>,
    val difficulty: SudokuDifficulty
)

fun sudokuRow(index: Int): Int = index / SUDOKU_SIZE
fun sudokuCol(index: Int): Int = index % SUDOKU_SIZE
fun sudokuBox(index: Int): Int = (sudokuRow(index) / SUDOKU_BOX) * SUDOKU_BOX + sudokuCol(index) / SUDOKU_BOX

/** Whether two cells share a row, column or 3x3 box. */
fun sudokuPeers(a: Int, b: Int): Boolean =
    a != b && (sudokuRow(a) == sudokuRow(b) || sudokuCol(a) == sudokuCol(b) || sudokuBox(a) == sudokuBox(b))

object SudokuRules {

    /** Every filled cell whose digit repeats in its row, column or box. */
    fun conflicts(grid: List<Int>): Set<Int> {
        val clashing = mutableSetOf<Int>()
        for (a in 0 until SUDOKU_CELLS) {
            if (grid[a] == 0) continue
            for (b in a + 1 until SUDOKU_CELLS) {
                if (grid[b] == grid[a] && sudokuPeers(a, b)) {
                    clashing += a
                    clashing += b
                }
            }
        }
        return clashing
    }

    fun isComplete(grid: List<Int>): Boolean = grid.size == SUDOKU_CELLS && grid.all { it in 1..SUDOKU_SIZE }

    /**
     * True when [grid] is a finished, valid Sudoku that keeps every one of
     * [givens]: all 81 cells 1..9 with no digit repeated in a row, column or
     * box. A valid fill of a unique-solution puzzle is its solution.
     */
    fun isSolved(givens: List<Int>, grid: List<Int>): Boolean =
        givens.size == SUDOKU_CELLS &&
            isComplete(grid) &&
            givens.indices.all { givens[it] == 0 || givens[it] == grid[it] } &&
            conflicts(grid).isEmpty()
}

object SudokuSolver {

    /**
     * How many solutions [grid] has, counting no further than [limit] -
     * [limit] = 2 is enough to tell "unique" from "not". A grid that already
     * breaks the rules has none.
     */
    fun countSolutions(grid: List<Int>, limit: Int = 2): Int {
        if (grid.size != SUDOKU_CELLS || SudokuRules.conflicts(grid).isNotEmpty()) return 0
        val cells = grid.toIntArray()
        val rows = IntArray(SUDOKU_SIZE)
        val cols = IntArray(SUDOKU_SIZE)
        val boxes = IntArray(SUDOKU_SIZE)
        for (i in 0 until SUDOKU_CELLS) {
            val d = cells[i]
            if (d != 0) mark(i, d, rows, cols, boxes, on = true)
        }
        var found = 0
        fun search() {
            // Fill the empty cell with the fewest candidates first; it keeps the search tiny.
            var best = -1
            var bestMask = 0
            var bestCount = 10
            for (i in 0 until SUDOKU_CELLS) {
                if (cells[i] != 0) continue
                val mask = candidates(i, rows, cols, boxes)
                val count = Integer.bitCount(mask)
                if (count < bestCount) {
                    best = i
                    bestMask = mask
                    bestCount = count
                    if (count <= 1) break
                }
            }
            if (best == -1) {
                found++
                return
            }
            var mask = bestMask
            while (mask != 0 && found < limit) {
                val bit = mask and -mask
                mask -= bit
                val d = Integer.numberOfTrailingZeros(bit)
                cells[best] = d
                mark(best, d, rows, cols, boxes, on = true)
                search()
                mark(best, d, rows, cols, boxes, on = false)
                cells[best] = 0
            }
        }
        search()
        return found
    }

    /** A solution to [grid], or null if it has none. */
    fun solve(grid: List<Int>, random: Random? = null): List<Int>? {
        if (grid.size != SUDOKU_CELLS || SudokuRules.conflicts(grid).isNotEmpty()) return null
        val cells = grid.toIntArray()
        val rows = IntArray(SUDOKU_SIZE)
        val cols = IntArray(SUDOKU_SIZE)
        val boxes = IntArray(SUDOKU_SIZE)
        for (i in 0 until SUDOKU_CELLS) if (cells[i] != 0) mark(i, cells[i], rows, cols, boxes, on = true)
        fun search(): Boolean {
            val i = cells.indexOfFirst { it == 0 }
            if (i == -1) return true
            val digits = (1..SUDOKU_SIZE).filter { candidates(i, rows, cols, boxes) and (1 shl it) != 0 }
            for (d in if (random != null) digits.shuffled(random) else digits) {
                cells[i] = d
                mark(i, d, rows, cols, boxes, on = true)
                if (search()) return true
                mark(i, d, rows, cols, boxes, on = false)
                cells[i] = 0
            }
            return false
        }
        return if (search()) cells.toList() else null
    }

    /** Digits 1..9 still open at [index], as bits 1..9. */
    private fun candidates(index: Int, rows: IntArray, cols: IntArray, boxes: IntArray): Int =
        ALL_DIGITS and (rows[sudokuRow(index)] or cols[sudokuCol(index)] or boxes[sudokuBox(index)]).inv()

    private fun mark(index: Int, digit: Int, rows: IntArray, cols: IntArray, boxes: IntArray, on: Boolean) {
        val bit = 1 shl digit
        if (on) {
            rows[sudokuRow(index)] = rows[sudokuRow(index)] or bit
            cols[sudokuCol(index)] = cols[sudokuCol(index)] or bit
            boxes[sudokuBox(index)] = boxes[sudokuBox(index)] or bit
        } else {
            rows[sudokuRow(index)] = rows[sudokuRow(index)] and bit.inv()
            cols[sudokuCol(index)] = cols[sudokuCol(index)] and bit.inv()
            boxes[sudokuBox(index)] = boxes[sudokuBox(index)] and bit.inv()
        }
    }

    private const val ALL_DIGITS = 0b11_1111_1110 // bits 1..9
}

object SudokuGenerator {

    /**
     * A new puzzle with exactly one solution. It fills a random complete grid,
     * then empties cells in random order, keeping each removal only if the
     * puzzle still has a single solution, until [difficulty]'s number of
     * givens is reached (or no more cells can go without losing uniqueness,
     * which only ever leaves a few extra givens on the hardest level).
     */
    fun generate(difficulty: SudokuDifficulty, random: Random = Random): SudokuPuzzle {
        val solution = requireNotNull(SudokuSolver.solve(List(SUDOKU_CELLS) { 0 }, random))
        val givens = solution.toMutableList()
        var filled = SUDOKU_CELLS
        for (index in (0 until SUDOKU_CELLS).shuffled(random)) {
            if (filled <= difficulty.givens) break
            val digit = givens[index]
            givens[index] = 0
            if (SudokuSolver.countSolutions(givens, limit = 2) == 1) {
                filled--
            } else {
                givens[index] = digit
            }
        }
        return SudokuPuzzle(givens.toList(), solution, difficulty)
    }
}
