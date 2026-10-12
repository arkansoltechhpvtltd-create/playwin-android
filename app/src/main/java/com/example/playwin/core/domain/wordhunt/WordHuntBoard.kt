package com.example.playwin.core.domain.wordhunt

import kotlin.random.Random

const val WORD_HUNT_GRID_SIZE = 9
const val WORD_HUNT_WORD_COUNT = 6

/** Seconds each player has to find a word on their turn before it passes. */
const val WORD_HUNT_TURN_SECONDS = 20
const val WORD_HUNT_TIME_WARNING_SECONDS = 5

/** The largest speed bonus a single find can earn (half the full turn clock). */
const val WORD_HUNT_MAX_BONUS = WORD_HUNT_TURN_SECONDS / 2

val WORD_HUNT_THEMED_WORDS = listOf(
    "LUDO", "CHESS", "CARROM", "SUDOKU", "COUPON", "WINNER",
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

/** Whether a start/end tap pair (in either order) selects exactly this word. */
fun PlacedWord.matches(first: Pair<Int, Int>, second: Pair<Int, Int>): Boolean {
    val start = startRow to startCol
    val end = endRow to endCol
    return (first == start && second == end) || (first == end && second == start)
}

/** One word found by a player, and the speed bonus they earned for it. */
data class WordHuntClaim(val word: String, val bonus: Int)

object WordHuntScoring {
    /** Seconds left on the finder's turn clock turn into a bonus: half of them, capped. */
    fun bonusFor(secondsLeft: Int): Int = (secondsLeft / 2).coerceIn(0, WORD_HUNT_MAX_BONUS)

    /** A claim is worth its word's length plus its (capped) speed bonus. */
    fun points(claim: WordHuntClaim): Int = claim.word.length + claim.bonus.coerceIn(0, WORD_HUNT_MAX_BONUS)

    fun total(claims: List<WordHuntClaim>): Int = claims.sumOf(::points)

    /** The most a player could still add by claiming every word in [unclaimed] at the maximum bonus. */
    fun maxRemaining(unclaimed: List<String>): Int = unclaimed.sumOf { it.length + WORD_HUNT_MAX_BONUS }
}

object WordHuntBoard {
    data class Generated(val grid: List<List<Char>>, val words: List<PlacedWord>)

    fun generate(random: Random = Random): Generated {
        val size = WORD_HUNT_GRID_SIZE
        val grid = Array(size) { arrayOfNulls<Char>(size) }
        val placed = mutableListOf<PlacedWord>()

        for (word in WORD_HUNT_THEMED_WORDS.shuffled(random)) {
            if (placed.size >= WORD_HUNT_WORD_COUNT) break
            placeWord(grid, word, random)?.let { placed += it }
        }

        val filledGrid = grid.map { row -> row.map { it ?: ('A' + random.nextInt(26)) } }
        return Generated(filledGrid, placed)
    }

    /** The puzzle for an online room, derived from its server-generated id so both devices agree. */
    fun forRoom(roomId: String): Generated = generate(Random(roomId.hashCode().toLong()))

    private fun placeWord(grid: Array<Array<Char?>>, word: String, random: Random): PlacedWord? {
        val size = WORD_HUNT_GRID_SIZE
        repeat(300) {
            val horizontal = random.nextBoolean()
            val row: Int
            val col: Int
            if (horizontal) {
                row = random.nextInt(size)
                col = random.nextInt(size - word.length + 1)
            } else {
                row = random.nextInt(size - word.length + 1)
                col = random.nextInt(size)
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
