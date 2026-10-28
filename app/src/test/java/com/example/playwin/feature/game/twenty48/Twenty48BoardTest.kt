package com.example.playwin.feature.game.twenty48

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Twenty48BoardTest {

    private fun row(vararg cells: Int) = listOf(listOf(*cells.toTypedArray()), List(4) { 0 }, List(4) { 0 }, List(4) { 0 })

    @Test
    fun `each tile merges at most once per move`() {
        assertEquals(listOf(4, 4, 0, 0), Twenty48Board.move(row(2, 2, 2, 2), SwipeDirection.LEFT).first[0])
        assertEquals(listOf(4, 4, 0, 0), Twenty48Board.move(row(2, 2, 4, 0), SwipeDirection.LEFT).first[0])
        assertEquals(listOf(4, 2, 0, 0), Twenty48Board.move(row(2, 2, 2, 0), SwipeDirection.LEFT).first[0])
    }

    @Test
    fun `merges pair up from the side being swiped towards`() {
        assertEquals(listOf(0, 0, 2, 4), Twenty48Board.move(row(2, 2, 2, 0), SwipeDirection.RIGHT).first[0])
    }

    @Test
    fun `up and down slide columns`() {
        val board = listOf(
            listOf(2, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(2, 0, 0, 0),
            listOf(4, 0, 0, 0)
        )
        assertEquals(listOf(4, 4, 0, 0), Twenty48Board.move(board, SwipeDirection.UP).first.map { it[0] })
        assertEquals(listOf(0, 0, 4, 4), Twenty48Board.move(board, SwipeDirection.DOWN).first.map { it[0] })
    }

    @Test
    fun `a swipe that changes nothing is not a move`() {
        assertFalse(Twenty48Board.move(row(2, 4, 0, 0), SwipeDirection.LEFT).second)
        assertTrue(Twenty48Board.move(row(2, 4, 0, 0), SwipeDirection.RIGHT).second)
    }

    @Test
    fun `game over only when the board is full with no equal neighbours`() {
        val stuck = listOf(
            listOf(2, 4, 2, 4),
            listOf(4, 2, 4, 2),
            listOf(2, 4, 2, 4),
            listOf(4, 2, 4, 2)
        )
        assertFalse(Twenty48Board.hasMovesLeft(stuck))
        assertTrue(Twenty48Board.hasMovesLeft(stuck.mapIndexed { r, cells -> if (r == 3) listOf(4, 2, 4, 4) else cells }))
        assertTrue(Twenty48Board.hasMovesLeft(stuck.mapIndexed { r, cells -> if (r == 0) listOf(0, 4, 2, 4) else cells }))
    }

    @Test
    fun `a spawn fills exactly one empty cell with a 2 or a 4`() {
        val before = row(2, 4, 0, 0)
        val after = Twenty48Board.spawnRandomTile(before)
        val changed = (0 until 4).flatMap { r -> (0 until 4).map { c -> r to c } }.filter { (r, c) -> before[r][c] != after[r][c] }
        assertEquals(1, changed.size)
        val (r, c) = changed.single()
        assertEquals(0, before[r][c])
        assertTrue(after[r][c] == 2 || after[r][c] == 4)
    }
}
