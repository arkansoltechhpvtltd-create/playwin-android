package com.example.playwin.core.domain.twenty48

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class Twenty48RulesTest {

    private fun board(vararg rows: List<Int>) = rows.toList()
    private fun row(vararg cells: Int) = cells.toList()
    private val empty = row(0, 0, 0, 0)

    @Test
    fun `a tile merges only once per move - no chained double merge`() {
        val (after, moved) = Twenty48Rules.move(board(row(4, 2, 2, 0), empty, empty, empty), SwipeDirection.LEFT)
        // The fresh 4 from 2+2 doesn't then merge with the 4 beside it.
        assertEquals(row(4, 4, 0, 0), after[0])
        assertTrue(moved)
        assertEquals(row(8, 8, 0, 0), Twenty48Rules.move(board(row(4, 4, 4, 4), empty, empty, empty), SwipeDirection.LEFT).first[0])
    }

    @Test
    fun `all four directions slide and merge towards their own wall`() {
        val start = board(row(2, 0, 0, 2), empty, empty, row(2, 0, 0, 0))
        assertEquals(row(4, 0, 0, 0), Twenty48Rules.move(start, SwipeDirection.LEFT).first[0])
        assertEquals(row(0, 0, 0, 4), Twenty48Rules.move(start, SwipeDirection.RIGHT).first[0])
        val up = Twenty48Rules.move(start, SwipeDirection.UP).first
        assertEquals(row(4, 0, 0, 2), up[0])
        val down = Twenty48Rules.move(start, SwipeDirection.DOWN).first
        assertEquals(row(4, 0, 0, 2), down[3])
    }

    @Test
    fun `a swipe that changes nothing is not a move`() {
        val packed = board(row(2, 4, 0, 0), empty, empty, empty)
        val (after, moved) = Twenty48Rules.move(packed, SwipeDirection.LEFT)
        assertEquals(packed, after)
        assertFalse(moved)
    }

    @Test
    fun `a spawn adds exactly one 2 or 4 in an empty cell`() {
        repeat(50) { seed ->
            val before = board(row(2, 4, 8, 16), row(0, 0, 0, 0), row(32, 64, 128, 256), row(2, 2, 2, 0))
            val after = Twenty48Rules.spawnRandomTile(before, Random(seed))
            val changed = (0 until 4).flatMap { r -> (0 until 4).map { c -> r to c } }.filter { (r, c) -> before[r][c] != after[r][c] }
            assertEquals(1, changed.size)
            val (r, c) = changed.single()
            assertEquals(0, before[r][c])
            assertTrue(after[r][c] == 2 || after[r][c] == 4)
        }
    }

    @Test
    fun `the game is over only when the board is full with no equal neighbours`() {
        val stuck = board(row(2, 4, 2, 4), row(4, 2, 4, 2), row(2, 4, 2, 4), row(4, 2, 4, 2))
        assertFalse(Twenty48Rules.hasMovesLeft(stuck))
        assertTrue(Twenty48Rules.hasMovesLeft(stuck.mapIndexed { r, cells -> if (r == 1) row(4, 2, 2, 2) else cells }))
    }

    @Test
    fun `win tile and well-formed boards`() {
        assertTrue(Twenty48Rules.hasReachedWinTile(board(row(2048, 0, 0, 0), empty, empty, empty)))
        assertTrue(Twenty48Rules.hasReachedWinTile(board(row(4096, 0, 0, 0), empty, empty, empty)))
        assertFalse(Twenty48Rules.hasReachedWinTile(board(row(1024, 1024, 0, 0), empty, empty, empty)))
        assertTrue(Twenty48Rules.isWellFormed(board(row(2, 4, 0, 2048), empty, empty, empty)))
        assertFalse(Twenty48Rules.isWellFormed(board(row(1, 0, 0, 0), empty, empty, empty)))
        assertFalse(Twenty48Rules.isWellFormed(board(row(6, 0, 0, 0), empty, empty, empty)))
    }
}
