package com.example.playwin.feature.game.wordhunt

const val WORD_HUNT_GRID_SIZE = 9
const val WORD_HUNT_WORD_COUNT = 6
const val WORD_HUNT_TIME_SECONDS = 90
const val WORD_HUNT_TIME_WARNING_SECONDS = 15

val WORD_HUNT_THEMED_WORDS = listOf(
    "LUDO", "CHESS", "CARROM", "BINGO", "COUPON", "WINNER",
    "PLAYER", "POCKET", "BOARD", "SCORE", "TOKEN", "GAMES"
)

/** A hidden word's start/end cell, placed either left-to-right or top-to-bottom. */
data class PlacedWord(
    val word: String,
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int
)

/** Every cell this word occupies, inclusive of its start and end. */
fun PlacedWord.cells(): List<Pair<Int, Int>> =
    if (startRow == endRow) {
        (startCol..endCol).map { c -> startRow to c }
    } else {
        (startRow..endRow).map { r -> r to startCol }
    }

object WordHuntBoard {
    data class Generated(val grid: List<List<Char>>, val words: List<PlacedWord>)

    fun generate(): Generated {
        val size = WORD_HUNT_GRID_SIZE
        val grid = Array(size) { arrayOfNulls<Char>(size) }
        val placed = mutableListOf<PlacedWord>()

        for (word in WORD_HUNT_THEMED_WORDS.shuffled()) {
            if (placed.size >= WORD_HUNT_WORD_COUNT) break
            placeWord(grid, word)?.let { placed += it }
        }

        val filledGrid = grid.map { row -> row.map { it ?: ('A' + (0 until 26).random()) } }
        return Generated(filledGrid, placed)
    }

    private fun placeWord(grid: Array<Array<Char?>>, word: String): PlacedWord? {
        val size = WORD_HUNT_GRID_SIZE
        repeat(300) {
            val horizontal = (0..1).random() == 0
            val row: Int
            val col: Int
            if (horizontal) {
                row = (0 until size).random()
                col = (0..(size - word.length)).random()
            } else {
                row = (0..(size - word.length)).random()
                col = (0 until size).random()
            }

            val fits = word.indices.all { i ->
                val r = if (horizontal) row else row + i
                val c = if (horizontal) col + i else col
                val existing = grid[r][c]
                existing == null || existing == word[i]
            }

            if (fits) {
                word.indices.forEach { i ->
                    val r = if (horizontal) row else row + i
                    val c = if (horizontal) col + i else col
                    grid[r][c] = word[i]
                }
                val endRow = if (horizontal) row else row + word.length - 1
                val endCol = if (horizontal) col + word.length - 1 else col
                return PlacedWord(word, row, col, endRow, endCol)
            }
        }
        return null
    }
}
