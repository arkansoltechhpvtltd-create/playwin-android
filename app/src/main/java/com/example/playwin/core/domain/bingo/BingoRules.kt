package com.example.playwin.core.domain.bingo

import kotlin.random.Random

const val BINGO_SIZE = 5
const val BINGO_FREE_ROW = 2
const val BINGO_FREE_COL = 2
const val BINGO_MAX_NUMBER = 75

val BINGO_LETTERS = listOf("B", "I", "N", "G", "O")
val BINGO_COLUMN_RANGES = listOf(1..15, 16..30, 31..45, 46..60, 61..75)

fun bingoLetterFor(number: Int): String {
    val columnIndex = BINGO_COLUMN_RANGES.indexOfFirst { number in it }
    return if (columnIndex >= 0) BINGO_LETTERS[columnIndex] else "?"
}

/**
 * Head-to-head 75-ball bingo mechanics: each player has their own card,
 * players take turns calling a number, and a called number marks every card
 * it's on. Pure Kotlin, no Android. Whether a card has actually won is still
 * confirmed by [com.example.playwin.core.domain.repository.GameEngineRepository];
 * [hasLine] here exists for [BingoBot]'s planning and the UI's hints.
 */
object BingoRules {

    /** A fresh 5x5 card: each column draws 5 unique numbers from its own range; the free centre is 0. */
    fun generateCard(random: Random = Random): List<List<Int>> {
        val columns = BINGO_COLUMN_RANGES.map { range -> range.shuffled(random).take(BINGO_SIZE) }
        return (0 until BINGO_SIZE).map { row ->
            (0 until BINGO_SIZE).map { col ->
                if (row == BINGO_FREE_ROW && col == BINGO_FREE_COL) 0 else columns[col][row]
            }
        }
    }

    /**
     * The two cards (host's, guest's) for an online room, derived from its
     * server-generated id so both devices agree and neither player can pick
     * their own card.
     */
    fun cardsForRoom(roomId: String): Pair<List<List<Int>>, List<List<Int>>> {
        val random = Random(roomId.hashCode().toLong())
        return generateCard(random) to generateCard(random)
    }

    /** Which squares of [card] are marked once [called] numbers have been called (the free centre always is). */
    fun marks(card: List<List<Int>>, called: Collection<Int>): List<List<Boolean>> =
        card.mapIndexed { r, row ->
            row.mapIndexed { c, n -> (r == BINGO_FREE_ROW && c == BINGO_FREE_COL) || n in called }
        }

    /** Every line on the card as a list of (row, col) cells: 5 rows, 5 columns, 2 diagonals. */
    val LINES: List<List<Pair<Int, Int>>> = buildList {
        for (r in 0 until BINGO_SIZE) add((0 until BINGO_SIZE).map { r to it })
        for (c in 0 until BINGO_SIZE) add((0 until BINGO_SIZE).map { it to c })
        add((0 until BINGO_SIZE).map { it to it })
        add((0 until BINGO_SIZE).map { it to BINGO_SIZE - 1 - it })
    }

    fun hasLine(marks: List<List<Boolean>>): Boolean = LINES.any { line -> line.all { (r, c) -> marks[r][c] } }

    fun uncalledNumbers(called: Collection<Int>): List<Int> = (1..BINGO_MAX_NUMBER).filter { it !in called }

    /** A random number nobody has called yet, or null once all 75 are gone. */
    fun randomUncalled(called: Collection<Int>, random: Random = Random): Int? =
        uncalledNumbers(called).randomOrNull(random)
}
