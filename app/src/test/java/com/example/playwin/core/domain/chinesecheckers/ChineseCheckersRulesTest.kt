package com.example.playwin.core.domain.chinesecheckers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseCheckersRulesTest {

    private fun at(q: Int, r: Int): Int = requireNotNull(ChineseCheckersBoard.indexOf(Hole(q, r))) { "($q, $r) is off the star" }

    /** An otherwise empty board with [pegs] placed, [toMove] to move. */
    private fun board(vararg pegs: Pair<Int, Int>, toMove: Int = 1): ChineseCheckersPosition {
        val cells = MutableList(ChineseCheckersBoard.holes.size) { 0 }
        pegs.forEach { (hole, player) -> cells[hole] = player }
        return ChineseCheckersPosition(cells, toMove)
    }

    private fun destinations(position: ChineseCheckersPosition, from: Int): Set<Int> =
        ChineseCheckersRules.movesFrom(position, from).map { it.to }.toSet()

    @Test
    fun `the star has 121 holes - a 61-hole middle and six points of 10`() {
        val holes = ChineseCheckersBoard.holes
        assertEquals(121, holes.size)
        assertEquals(61, holes.count { maxOf(kotlin.math.abs(it.q), kotlin.math.abs(it.r), kotlin.math.abs(it.s)) <= 4 })
        assertEquals(10, ChineseCheckersBoard.home(1).size)
        assertEquals(10, ChineseCheckersBoard.home(2).size)
        // The two homes are opposite points: each player's goal is the other's home.
        assertEquals(ChineseCheckersBoard.home(2), ChineseCheckersBoard.goal(1))
        assertEquals(ChineseCheckersBoard.home(1), ChineseCheckersBoard.goal(2))
        assertTrue(ChineseCheckersBoard.home(1).all { holes[it].r > 4 })
        assertTrue(ChineseCheckersBoard.home(2).all { holes[it].r < -4 })
    }

    @Test
    fun `each player starts with 10 pegs in their home point, player 1 to move`() {
        val start = ChineseCheckersRules.initialPosition()
        assertEquals(ChineseCheckersBoard.home(1), start.pegsOf(1).toSet())
        assertEquals(ChineseCheckersBoard.home(2), start.pegsOf(2).toSet())
        assertEquals(1, start.sideToMove)
        assertTrue(ChineseCheckersRules.legalMoves(start).isNotEmpty())
    }

    @Test
    fun `a single step to an adjacent empty hole is legal`() {
        val position = board(at(0, 0) to 1)
        val move = ChineseCheckersRules.legalMove(position, at(0, 0), at(1, 0))
        assertNotNull(move)
        assertFalse(move!!.isJump)
        // All six neighbours of a lone peg in the middle are open, and nothing further.
        assertEquals(
            setOf(at(1, 0), at(-1, 0), at(0, 1), at(0, -1), at(1, -1), at(-1, 1)),
            destinations(position, at(0, 0))
        )
    }

    @Test
    fun `a step onto an occupied hole, or two holes away, is not legal`() {
        val position = board(at(0, 0) to 1, at(1, 0) to 2, at(2, 0) to 2)
        assertNull(ChineseCheckersRules.legalMove(position, at(0, 0), at(1, 0)))
        assertNull(ChineseCheckersRules.legalMove(position, at(0, 0), at(0, -2)))
    }

    @Test
    fun `a jump over an adjacent peg into the empty hole beyond is legal - over either colour`() {
        listOf(1, 2).forEach { overColour ->
            val position = board(at(0, 0) to 1, at(1, 0) to overColour)
            val move = ChineseCheckersRules.legalMove(position, at(0, 0), at(2, 0))
            assertNotNull("over player $overColour", move)
            assertTrue(move!!.isJump)
            assertEquals(listOf(at(2, 0)), move.path)
        }
    }

    @Test
    fun `no jump into an occupied hole, over an empty one, or over two pegs`() {
        // Landing hole taken.
        assertNull(ChineseCheckersRules.legalMove(board(at(0, 0) to 1, at(1, 0) to 2, at(2, 0) to 2), at(0, 0), at(2, 0)))
        // Nothing to jump over.
        assertNull(ChineseCheckersRules.legalMove(board(at(0, 0) to 1), at(0, 0), at(2, 0)))
        // Two pegs in a row can't be jumped together.
        assertNull(ChineseCheckersRules.legalMove(board(at(0, 0) to 1, at(1, 0) to 2, at(2, 0) to 2), at(0, 0), at(3, 0)))
    }

    @Test
    fun `a chained double jump is legal, landing on each hole in turn`() {
        // Over (1,0) to (2,0), then over (2,-1) to (2,-2).
        val position = board(at(0, 0) to 1, at(1, 0) to 2, at(2, -1) to 1)
        val move = ChineseCheckersRules.legalMove(position, at(0, 0), at(2, -2))
        assertNotNull(move)
        assertTrue(move!!.isJump)
        assertEquals(listOf(at(2, 0), at(2, -2)), move.path)
        // The first landing is a destination too: a chain may stop at any point.
        assertNotNull(ChineseCheckersRules.legalMove(position, at(0, 0), at(2, 0)))
    }

    @Test
    fun `a chain can't jump back over the hole the peg left`() {
        // After jumping (0,0) -> (2,0) over (1,0), jumping back over (1,0) would land on the
        // start, which is the same as not moving - so it's not offered.
        val position = board(at(0, 0) to 1, at(1, 0) to 2)
        assertFalse(at(0, 0) in destinations(position, at(0, 0)))
    }

    @Test
    fun `only the side to move's pegs move`() {
        val position = board(at(0, 0) to 2, toMove = 1)
        assertTrue(ChineseCheckersRules.movesFrom(position, at(0, 0)).isEmpty())
    }

    @Test
    fun `jumping never captures - every peg stays on the board`() {
        val position = board(at(0, 0) to 1, at(1, 0) to 2)
        val after = ChineseCheckersRules.play(position, ChineseCheckersRules.legalMove(position, at(0, 0), at(2, 0))!!)
        assertEquals(listOf(at(2, 0)), after.pegsOf(1))
        assertEquals(listOf(at(1, 0)), after.pegsOf(2))
        assertEquals(2, after.sideToMove)
    }

    @Test
    fun `a win is all 10 pegs in the opposite point, and only then`() {
        val goal = ChineseCheckersBoard.goal(1).toList()
        val nine = goal.drop(1).map { it to 1 }.toTypedArray()
        val lastHole = goal.first()
        assertFalse(ChineseCheckersRules.hasWon(board(*nine), 1))

        val all = board(*nine, lastHole to 1)
        assertTrue(ChineseCheckersRules.hasWon(all, 1))
        assertFalse(ChineseCheckersRules.hasWon(all, 2))
        assertEquals(10, ChineseCheckersRules.pegsInGoal(all, 1))
    }

    @Test
    fun `the starting position is no win for either side`() {
        val start = ChineseCheckersRules.initialPosition()
        assertFalse(ChineseCheckersRules.hasWon(start, 1))
        assertFalse(ChineseCheckersRules.hasWon(start, 2))
    }
}
