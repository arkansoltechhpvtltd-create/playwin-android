package com.example.playwin.feature.game.ludo

import com.example.playwin.core.domain.ludo.LudoColor
import com.example.playwin.core.domain.ludo.LudoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/** The token path laid onto ludo_board.png. Cells are (row, col), 0..14 from the top-left. */
class LudoBoardLayoutTest {

    /** Next to each other, including diagonally (the track turns diagonally at the cross's inner corners). */
    private fun touching(a: Pair<Int, Int>, b: Pair<Int, Int>) =
        max(abs(a.first - b.first), abs(a.second - b.second)) == 1

    private fun ringAt(color: LudoColor, steps: Int) = RING_CELLS[LudoEngine.globalIndexOf(color, steps)]

    @Test
    fun `the track is 52 distinct cells, each next to the one before, closing the loop`() {
        assertEquals(LudoEngine.SHARED_PATH_LENGTH, RING_CELLS.size)
        assertEquals(RING_CELLS.size, RING_CELLS.toSet().size)
        RING_CELLS.indices.forEach { i ->
            assertTrue("cells $i and ${i + 1}", touching(RING_CELLS[i], RING_CELLS[(i + 1) % RING_CELLS.size]))
        }
    }

    @Test
    fun `each colour starts on its painted start square`() {
        assertEquals(6 to 1, ringAt(LudoColor.RED, 0))
        assertEquals(1 to 8, ringAt(LudoColor.GREEN, 0))
        assertEquals(8 to 13, ringAt(LudoColor.YELLOW, 0))
        assertEquals(13 to 6, ringAt(LudoColor.BLUE, 0))
    }

    @Test
    fun `each colour turns off at its painted arrow, straight into its home lane`() {
        val turnOffs = mapOf(
            LudoColor.RED to (7 to 0),
            LudoColor.GREEN to (0 to 7),
            LudoColor.YELLOW to (7 to 14),
            LudoColor.BLUE to (14 to 7)
        )
        for ((color, cell) in turnOffs) {
            val last = ringAt(color, LudoEngine.PLAYER_PATH_LENGTH - 1)
            assertEquals(cell, last)
            val first = homeColumnCells(color).first()
            assertEquals("$color steps one cell, not diagonally", 1, abs(first.first - last.first) + abs(first.second - last.second))
        }
    }

    @Test
    fun `home lanes run down the middle of each arm to the centre`() {
        assertEquals((1..5).map { 7 to it } + (7 to 7), homeColumnCells(LudoColor.RED))
        assertEquals((1..5).map { it to 7 } + (7 to 7), homeColumnCells(LudoColor.GREEN))
        assertEquals((13 downTo 9).map { 7 to it } + (7 to 7), homeColumnCells(LudoColor.YELLOW))
        assertEquals((13 downTo 9).map { it to 7 } + (7 to 7), homeColumnCells(LudoColor.BLUE))
        LudoEngine.PLAYER_ORDER.forEach { assertEquals(LudoEngine.HOME_COLUMN_LENGTH, homeColumnCells(it).size) }
    }

    @Test
    fun `the safe squares are the painted stars and start squares`() {
        val safeCells = LudoEngine.SAFE_GLOBAL_INDICES.map { RING_CELLS[it] }.toSet()
        val stars = setOf(2 to 6, 6 to 12, 12 to 8, 8 to 2)
        val starts = setOf(6 to 1, 1 to 8, 8 to 13, 13 to 6)
        assertEquals(stars + starts, safeCells)
    }

    @Test
    fun `cells are drawn at their centres`() {
        assertEquals(1.5f, gridPoint(6 to 1).x, 0.001f)
        assertEquals(6.5f, gridPoint(6 to 1).y, 0.001f)
        assertEquals(7.5f, gridPoint(7 to 7).x, 0.001f)
    }

    @Test
    fun `the layout is the same for every colour, a quarter turn apart`() {
        // A quarter turn clockwise about the board's centre takes (row, col) to (col, 14 - row).
        fun quarterTurn(cell: Pair<Int, Int>) = cell.second to (14 - cell.first)
        LudoEngine.PLAYER_ORDER.zipWithNext().forEach { (color, next) ->
            (0 until LudoEngine.PLAYER_PATH_LENGTH).forEach { step ->
                assertEquals("$color step $step", ringAt(next, step), quarterTurn(ringAt(color, step)))
            }
            assertEquals(homeColumnCells(next), homeColumnCells(color).map(::quarterTurn))
        }
    }
}
