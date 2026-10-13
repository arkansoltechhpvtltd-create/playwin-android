package com.example.playwin.core.domain.wordhunt

import kotlin.random.Random

const val WORD_HUNT_GRID_SIZE = 9
const val WORD_HUNT_WORD_COUNT = 6

/** Seconds each player has to find a word on their turn before it passes. */
const val WORD_HUNT_TURN_SECONDS = 20

/**
 * The whole game's clock, counted across both players' turns. When it runs
 * out the game ends there and the higher score wins. Tune it here.
 */
const val WORD_HUNT_GAME_SECONDS = 180
const val WORD_HUNT_TIME_WARNING_SECONDS = 5

/** The largest speed bonus a single find can earn (half the full turn clock). */
const val WORD_HUNT_MAX_BONUS = WORD_HUNT_TURN_SECONDS / 2

val WORD_HUNT_THEMED_WORDS = listOf(
    "LUDO", "CHESS", "CARROM", "SUDOKU", "COUPON", "WINNER",
    "PLAYER", "POCKET", "BOARD", "SCORE", "TOKEN", "GAMES"
)

/**
 * The eight ways a word can run, as a (row, column) step from one letter to the
 * next: across and down, backwards along both, and the four diagonals.
 */
val WORD_HUNT_DIRECTIONS: List<Pair<Int, Int>> = listOf(
    0 to 1, 0 to -1, 1 to 0, -1 to 0,
    1 to 1, 1 to -1, -1 to 1, -1 to -1
)

/**
 * A hidden word's first and last letter cells. It reads in a straight line from
 * start to end in any of [WORD_HUNT_DIRECTIONS], so backwards or diagonally too.
 */
data class PlacedWord(
    val word: String,
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int
)

/** Every cell this word occupies, from its first letter to its last. */
fun PlacedWord.cells(): List<Pair<Int, Int>> {
    val dr = (endRow - startRow).coerceIn(-1, 1)
    val dc = (endCol - startCol).coerceIn(-1, 1)
    return word.indices.map { i -> (startRow + dr * i) to (startCol + dc * i) }
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

    /** Hides [word] in a random direction and spot where it fits, sharing letters it agrees with. */
    private fun placeWord(grid: Array<Array<Char?>>, word: String, random: Random): PlacedWord? {
        val size = WORD_HUNT_GRID_SIZE
        val span = word.length - 1
        repeat(300) {
            val (dr, dc) = WORD_HUNT_DIRECTIONS[random.nextInt(WORD_HUNT_DIRECTIONS.size)]
            // A start from which the whole word, stepping (dr, dc), stays on the grid.
            val rowRange = if (dr < 0) span until size else 0 until size - dr * span
            val colRange = if (dc < 0) span until size else 0 until size - dc * span
            val row = rowRange.random(random)
            val col = colRange.random(random)

            val fits = word.indices.all { i ->
                val existing = grid[row + dr * i][col + dc * i]
                existing == null || existing == word[i]
            }

            if (fits) {
                word.indices.forEach { i -> grid[row + dr * i][col + dc * i] = word[i] }
                return PlacedWord(word, row, col, row + dr * span, col + dc * span)
            }
        }
        return null
    }
}
