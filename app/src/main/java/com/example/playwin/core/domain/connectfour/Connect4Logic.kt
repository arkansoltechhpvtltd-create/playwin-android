package com.example.playwin.core.domain.connectfour

/**
 * Pure Connect 4 board mechanics shared by the vs-Computer ViewModel,
 * [Connect4Bot] and the online-multiplayer ViewModel, so "where does a dropped piece land" is
 * defined once. Never decides who won - that's still
 * [com.example.playwin.core.domain.repository.GameEngineRepository]'s job.
 */
object Connect4Logic {
    const val ROWS = 6
    const val COLS = 7

    fun emptyBoard(rows: Int = ROWS, cols: Int = COLS): List<List<Int>> =
        List(rows) { List(cols) { 0 } }

    fun lowestEmptyRow(board: List<List<Int>>, col: Int): Int? {
        for (row in board.indices.reversed()) {
            if (board[row][col] == 0) return row
        }
        return null
    }

    /** Returns the board with [player]'s piece dropped into [col], or null if the column is full. */
    fun placePiece(board: List<List<Int>>, col: Int, player: Int): List<List<Int>>? {
        val row = lowestEmptyRow(board, col) ?: return null
        return board.mapIndexed { r, rowCells ->
            if (r == row) rowCells.mapIndexed { c, cell -> if (c == col) player else cell } else rowCells
        }
    }

    fun isBoardFull(board: List<List<Int>>): Boolean = board.all { row -> row.all { it != 0 } }
}
