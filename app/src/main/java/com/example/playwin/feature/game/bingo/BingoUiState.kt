package com.example.playwin.feature.game.bingo

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

/** Builds a fresh 5x5 card: each column draws 5 unique numbers from its own range, center square is free (0). */
fun generateBingoCard(): List<List<Int>> {
    val columns = BINGO_COLUMN_RANGES.map { range -> range.shuffled().take(BINGO_SIZE) }
    return (0 until BINGO_SIZE).map { row ->
        (0 until BINGO_SIZE).map { col ->
            if (row == BINGO_FREE_ROW && col == BINGO_FREE_COL) 0 else columns[col][row]
        }
    }
}

private fun defaultDaubed(): List<List<Boolean>> =
    (0 until BINGO_SIZE).map { row ->
        (0 until BINGO_SIZE).map { col -> row == BINGO_FREE_ROW && col == BINGO_FREE_COL }
    }

data class BingoUiState(
    val card: List<List<Int>> = generateBingoCard(),
    val daubed: List<List<Boolean>> = defaultDaubed(),
    val calledNumbers: List<Int> = emptyList(),
    val currentCall: Int? = null,
    val hasWon: Boolean = false,
    val isBoardExhausted: Boolean = false,
    val isResolving: Boolean = false,
    val wonCouponId: String? = null
)
