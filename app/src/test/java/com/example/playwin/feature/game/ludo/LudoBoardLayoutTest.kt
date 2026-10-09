package com.example.playwin.feature.game.ludo

import com.example.playwin.core.domain.ludo.LudoColor
import com.example.playwin.core.domain.ludo.LudoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LudoBoardLayoutTest {

    private fun adjacent(a: Pair<Int, Int>, b: Pair<Int, Int>) =
        abs(a.first - b.first) + abs(a.second - b.second) == 1

    @Test
    fun `the ring is 52 distinct cells, each next to the one before, closing the loop`() {
        assertEquals(LudoEngine.SHARED_PATH_LENGTH, RING_CELLS.size)
        assertEquals(RING_CELLS.size, RING_CELLS.toSet().size)
        RING_CELLS.indices.forEach { i ->
            assertTrue("cells $i and ${i + 1}", adjacent(RING_CELLS[i], RING_CELLS[(i + 1) % RING_CELLS.size]))
        }
    }

    @Test
    fun `each colour leaves the ring right beside its home column`() {
        for (color in LudoEngine.PLAYER_ORDER) {
            val exit = RING_CELLS[LudoEngine.globalIndexOf(color, LudoEngine.PLAYER_PATH_LENGTH - 1)]
            val entry = homeColumnCells(color).first()
            // Home columns are drawn half a cell off the grid, down the board's centre lines,
            // so measure where the cells are drawn: the step in is about one and a half
            // cells (it was nearly five, diagonally, before the ring was re-indexed).
            val step = gridPoint(entry) - gridPoint(exit)
            assertTrue("$color leaves at $exit, enters at $entry: step $step", step.getDistance() < 1.6f)
        }
    }

    @Test
    fun `the layout is the same for every colour, a quarter turn apart`() {
        // Turning the board a quarter turn clockwise about its centre maps (row, col) to (col, 14 - row + 1).
        fun quarterTurn(cell: Pair<Int, Int>) = cell.second to (15 - cell.first)
        val order = LudoEngine.PLAYER_ORDER
        order.zipWithNext().forEach { (color, next) ->
            val start = RING_CELLS[LudoEngine.startIndexFor(color)]
            assertEquals(RING_CELLS[LudoEngine.startIndexFor(next)], quarterTurn(start))
        }
        assertEquals(LudoColor.RED, order.first())
    }
}
