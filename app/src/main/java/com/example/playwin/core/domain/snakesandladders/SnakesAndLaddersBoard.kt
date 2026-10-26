package com.example.playwin.core.domain.snakesandladders

/**
 * The fixed board: squares 1..100 on a 10x10 grid in boustrophedon order -
 * 1-10 left to right along the bottom row, 11-20 right to left above it, and
 * so on up to 100 at the top left - with a standard spread of ladders and
 * snakes. No two of them share a square, none starts on 1 or 100, and none
 * ends where another starts, so a landing triggers at most one jump.
 */
object SnakesAndLaddersBoard {
    const val COLUMNS = 10
    const val LAST_SQUARE = COLUMNS * COLUMNS

    /** Ladder bottom -> top. */
    val LADDERS: Map<Int, Int> = mapOf(
        4 to 14,
        9 to 31,
        20 to 38,
        28 to 84,
        36 to 44,
        40 to 59,
        51 to 67,
        63 to 81,
        71 to 91
    )

    /** Snake head -> tail. */
    val SNAKES: Map<Int, Int> = mapOf(
        17 to 7,
        47 to 26,
        54 to 34,
        62 to 19,
        64 to 60,
        87 to 24,
        93 to 73,
        95 to 75,
        99 to 78
    )

    /** Where a token that lands on [square] ends up: a ladder's top, a snake's tail, or [square] itself. */
    fun destination(square: Int): Int = LADDERS[square] ?: SNAKES[square] ?: square

    /** The grid row of [square] counted from the bottom (0..9). */
    fun rowOf(square: Int): Int = (square - 1) / COLUMNS

    /** The grid column of [square] counted from the left (0..9): even rows run left to right, odd rows back. */
    fun columnOf(square: Int): Int {
        val along = (square - 1) % COLUMNS
        return if (rowOf(square) % 2 == 0) along else COLUMNS - 1 - along
    }

    /** The square at grid [row] (from the bottom) and [column] (from the left). */
    fun squareAt(row: Int, column: Int): Int {
        val along = if (row % 2 == 0) column else COLUMNS - 1 - column
        return row * COLUMNS + along + 1
    }
}
