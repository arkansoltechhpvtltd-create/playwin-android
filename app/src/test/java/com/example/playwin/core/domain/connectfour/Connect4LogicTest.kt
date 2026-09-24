package com.example.playwin.core.domain.connectfour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Connect4LogicTest {

    @Test
    fun `empty board is all zeros with the right shape`() {
        val board = Connect4Logic.emptyBoard()
        assertEquals(Connect4Logic.ROWS, board.size)
        assertTrue(board.all { row -> row.size == Connect4Logic.COLS && row.all { it == 0 } })
    }

    @Test
    fun `a piece drops to the lowest empty row in its column`() {
        val board = Connect4Logic.emptyBoard()
        val afterFirstDrop = Connect4Logic.placePiece(board, col = 3, player = 1)!!
        assertEquals(1, afterFirstDrop[Connect4Logic.ROWS - 1][3])

        val afterSecondDrop = Connect4Logic.placePiece(afterFirstDrop, col = 3, player = 2)!!
        assertEquals(2, afterSecondDrop[Connect4Logic.ROWS - 2][3])
        // The first piece is untouched.
        assertEquals(1, afterSecondDrop[Connect4Logic.ROWS - 1][3])
    }

    @Test
    fun `placing in a full column returns null instead of overwriting`() {
        var board = Connect4Logic.emptyBoard()
        repeat(Connect4Logic.ROWS) { i ->
            board = Connect4Logic.placePiece(board, col = 0, player = if (i % 2 == 0) 1 else 2)!!
        }
        assertNull(Connect4Logic.placePiece(board, col = 0, player = 1))
    }

    @Test
    fun `lowestEmptyRow returns null for a full column`() {
        var board = Connect4Logic.emptyBoard()
        repeat(Connect4Logic.ROWS) { board = Connect4Logic.placePiece(board, col = 5, player = 1)!! }
        assertNull(Connect4Logic.lowestEmptyRow(board, 5))
    }

    @Test
    fun `isBoardFull is false until every cell is occupied`() {
        var board = Connect4Logic.emptyBoard()
        assertFalse(Connect4Logic.isBoardFull(board))

        for (col in 0 until Connect4Logic.COLS) {
            repeat(Connect4Logic.ROWS) { i ->
                board = Connect4Logic.placePiece(board, col, if (i % 2 == 0) 1 else 2)!!
            }
        }
        assertTrue(Connect4Logic.isBoardFull(board))
    }

    @Test
    fun `placing a piece does not mutate the board passed in`() {
        val original = Connect4Logic.emptyBoard()
        Connect4Logic.placePiece(original, col = 2, player = 1)
        assertTrue(original.all { row -> row.all { it == 0 } })
    }
}
