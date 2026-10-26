package com.example.playwin.core.domain.snakesandladders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakesAndLaddersRulesTest {

    private fun at(mine: Int, theirs: Int = 0, turn: Int = 0, sixes: Int = 0) =
        SnakesAndLaddersState(positions = if (turn == 0) listOf(mine, theirs) else listOf(theirs, mine), turn = turn, sixesInARow = sixes)

    @Test
    fun `a roll moves the token forward that many squares and passes the turn`() {
        val turn = SnakesAndLaddersRules.play(at(10), 3)
        assertEquals(13, turn.move.landed)
        assertEquals(listOf(13, 0), turn.after.positions)
        assertEquals(1, turn.after.turn)
        assertFalse(turn.extraTurn)
        assertFalse(turn.won)
        assertEquals(1, turn.after.turnsPlayed)
    }

    @Test
    fun `the first roll enters the board from the start`() {
        val turn = SnakesAndLaddersRules.play(SnakesAndLaddersState(), 2)
        assertEquals(2, turn.after.positions[0])
    }

    @Test
    fun `a six grants the same player another roll`() {
        val turn = SnakesAndLaddersRules.play(at(10), 6)
        assertEquals(16, turn.after.positions[0])
        assertTrue(turn.extraTurn)
        assertEquals(0, turn.after.turn)
        assertEquals(1, turn.after.sixesInARow)
    }

    @Test
    fun `three sixes in a row forfeit the turn without moving`() {
        val second = SnakesAndLaddersRules.play(SnakesAndLaddersRules.play(at(10), 6).after, 6)
        assertTrue(second.extraTurn)
        val third = SnakesAndLaddersRules.play(second.after, 6)
        assertTrue(third.move.forfeited)
        assertFalse(third.extraTurn)
        assertEquals(second.after.positions, third.after.positions)
        assertEquals(1, third.after.turn)
        assertEquals(0, third.after.sixesInARow)
        assertEquals(SnakesAndLaddersRules.MAX_SIXES_IN_A_ROW, 3)
    }

    @Test
    fun `a non-six roll resets the run of sixes`() {
        val turn = SnakesAndLaddersRules.play(at(10, sixes = 2), 4)
        assertEquals(0, turn.after.sixesInARow)
        assertEquals(1, turn.after.turn)
    }

    @Test
    fun `landing on a ladder's foot climbs to its top`() {
        val turn = SnakesAndLaddersRules.play(at(25), 3) // 28 -> 84
        assertEquals(28, turn.move.landed)
        assertEquals(84, turn.move.to)
        assertTrue(turn.move.climbedLadder)
        assertEquals(84, turn.after.positions[0])
    }

    @Test
    fun `landing on a snake's head slides to its tail`() {
        val turn = SnakesAndLaddersRules.play(at(85), 2) // 87 -> 24
        assertEquals(87, turn.move.landed)
        assertEquals(24, turn.move.to)
        assertTrue(turn.move.hitSnake)
        assertEquals(24, turn.after.positions[0])
    }

    @Test
    fun `every ladder and snake moves the token as laid out`() {
        (SnakesAndLaddersBoard.LADDERS + SnakesAndLaddersBoard.SNAKES).forEach { (start, end) ->
            val turn = SnakesAndLaddersRules.play(at(start - 1), 1)
            assertEquals("landing on $start", end, turn.after.positions[0])
        }
    }

    @Test
    fun `reaching 100 exactly wins`() {
        val turn = SnakesAndLaddersRules.play(at(96), 4)
        assertTrue(turn.won)
        assertEquals(100, turn.after.positions[0])
        assertEquals(0, SnakesAndLaddersRules.winner(turn.after))
    }

    @Test
    fun `passing 100 also wins - no exact roll, no bounce back`() {
        val turn = SnakesAndLaddersRules.play(at(97), 5)
        assertTrue(turn.won)
        assertEquals(100, turn.move.landed)
        assertEquals(100, turn.after.positions[0])
    }

    @Test
    fun `a winning six doesn't give another roll`() {
        val turn = SnakesAndLaddersRules.play(at(96), 6)
        assertTrue(turn.won)
        assertFalse(turn.extraTurn)
    }

    @Test
    fun `the second player moves their own token`() {
        val turn = SnakesAndLaddersRules.play(at(mine = 30, theirs = 50, turn = 1), 2)
        assertEquals(listOf(50, 32), turn.after.positions)
        assertEquals(0, turn.after.turn)
        assertNull(SnakesAndLaddersRules.winner(turn.after))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a roll that no die can throw is rejected`() {
        SnakesAndLaddersRules.play(at(10), 7)
    }

    @Test
    fun `squares run boustrophedon from the bottom left`() {
        assertEquals(0 to 0, SnakesAndLaddersBoard.rowOf(1) to SnakesAndLaddersBoard.columnOf(1))
        assertEquals(0 to 9, SnakesAndLaddersBoard.rowOf(10) to SnakesAndLaddersBoard.columnOf(10))
        assertEquals(1 to 9, SnakesAndLaddersBoard.rowOf(11) to SnakesAndLaddersBoard.columnOf(11))
        assertEquals(1 to 0, SnakesAndLaddersBoard.rowOf(20) to SnakesAndLaddersBoard.columnOf(20))
        assertEquals(9 to 0, SnakesAndLaddersBoard.rowOf(100) to SnakesAndLaddersBoard.columnOf(100))
        for (square in 1..100) {
            assertEquals(square, SnakesAndLaddersBoard.squareAt(SnakesAndLaddersBoard.rowOf(square), SnakesAndLaddersBoard.columnOf(square)))
        }
    }

    @Test
    fun `the layout has no overlaps and nothing on the first or last square`() {
        val ladders = SnakesAndLaddersBoard.LADDERS
        val snakes = SnakesAndLaddersBoard.SNAKES
        assertTrue(ladders.all { (foot, top) -> top > foot })
        assertTrue(snakes.all { (head, tail) -> tail < head })
        val ends = (ladders.keys + ladders.values + snakes.keys + snakes.values).toList()
        assertEquals("every end on its own square", ends.size, ends.toSet().size)
        assertTrue(ends.all { it in 2..99 })
    }
}
