package com.example.playwin.core.domain.connectfour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class Connect4BotTest {

    private fun drop(board: List<List<Int>>, vararg moves: Pair<Int, Int>): List<List<Int>> =
        moves.fold(board) { b, (col, player) -> Connect4Logic.placePiece(b, col, player)!! }

    @Test
    fun `takes an immediate win over blocking`() {
        // Computer (2) has three in column 0; human (1) has three in column 6.
        val board = drop(
            Connect4Logic.emptyBoard(),
            0 to 2, 0 to 2, 0 to 2,
            6 to 1, 6 to 1, 6 to 1
        )
        repeat(20) {
            assertEquals(0, Connect4Bot.chooseColumn(board, player = 2, opponent = 1, random = Random(it)))
        }
    }

    @Test
    fun `blocks the opponent's immediate win`() {
        val board = drop(Connect4Logic.emptyBoard(), 1 to 1, 2 to 1, 3 to 1)
        val col = Connect4Bot.chooseColumn(board, player = 2, opponent = 1)
        assertTrue(col == 0 || col == 4)
    }

    @Test
    fun `avoids a move that sets up the opponent's win directly above it`() {
        // Bottom row: 2 1 2 _ _ _ _ ; second row: 1 1 1 _ _ _ _.
        // The human (1) wins at column 3's second row as soon as its bottom
        // cell is filled - so the computer must not be the one to fill it.
        val board = drop(
            Connect4Logic.emptyBoard(),
            0 to 2, 1 to 1, 2 to 2,
            0 to 1, 1 to 1, 2 to 1
        )
        repeat(20) {
            assertNotEquals(3, Connect4Bot.chooseColumn(board, player = 2, opponent = 1, random = Random(it)))
        }
    }

    @Test
    fun `never picks a full column and returns null on a full board`() {
        var board = Connect4Logic.emptyBoard()
        repeat(Connect4Logic.ROWS) { i -> board = Connect4Logic.placePiece(board, 3, if (i % 2 == 0) 1 else 2)!! }
        repeat(20) {
            assertNotEquals(3, Connect4Bot.chooseColumn(board, player = 2, opponent = 1, random = Random(it)))
        }

        val full = List(Connect4Logic.ROWS) { r -> List(Connect4Logic.COLS) { c -> if ((r / 2 + c) % 2 == 0) 1 else 2 } }
        assertNull(Connect4Bot.chooseColumn(full, player = 2, opponent = 1))
    }

    @Test
    fun `connectsFour detects horizontal, vertical and diagonal lines only`() {
        assertTrue(Connect4Bot.connectsFour(drop(Connect4Logic.emptyBoard(), 0 to 1, 1 to 1, 2 to 1, 3 to 1), 1))
        assertTrue(Connect4Bot.connectsFour(drop(Connect4Logic.emptyBoard(), 5 to 2, 5 to 2, 5 to 2, 5 to 2), 2))
        val diagonal = drop(
            Connect4Logic.emptyBoard(),
            0 to 1,
            1 to 2, 1 to 1,
            2 to 2, 2 to 2, 2 to 1,
            3 to 2, 3 to 2, 3 to 2, 3 to 1
        )
        assertTrue(Connect4Bot.connectsFour(diagonal, 1))
        assertFalse(Connect4Bot.connectsFour(drop(Connect4Logic.emptyBoard(), 0 to 1, 1 to 1, 2 to 1), 1))
    }
}
