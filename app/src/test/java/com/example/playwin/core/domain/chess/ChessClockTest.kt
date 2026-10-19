package com.example.playwin.core.domain.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessClockTest {

    private val fiveMinutes = CHESS_TIME_CONTROLS.first { it.seconds == 5 * 60 }
    private val unlimited = CHESS_TIME_CONTROLS.first { it.isUnlimited }

    @Test
    fun `the offered time controls, with ten minutes the default`() {
        assertEquals(listOf(300, 600, 1800, 3600, 10800, null), CHESS_TIME_CONTROLS.map { it.seconds })
        assertEquals(600, DEFAULT_TIME_CONTROL.seconds)
    }

    @Test
    fun `nothing runs until the first move, which starts the opponent's clock`() {
        val fresh = ChessClock(fiveMinutes)
        assertNull(fresh.running)
        assertEquals(300_000L, fresh.tick(10_000).whiteMillis)
        assertEquals(PieceColor.BLACK, fresh.afterMove(PieceColor.WHITE).running)
    }

    @Test
    fun `the clock switches to the other player on each move, and only the running one counts down`() {
        var clock = ChessClock(fiveMinutes).afterMove(PieceColor.WHITE) // Black to move
        clock = clock.tick(4_000)
        assertEquals(296_000L, clock.blackMillis)
        assertEquals(300_000L, clock.whiteMillis)

        clock = clock.afterMove(PieceColor.BLACK) // White to move
        assertEquals(PieceColor.WHITE, clock.running)
        clock = clock.tick(7_000)
        assertEquals(293_000L, clock.whiteMillis)
        assertEquals(296_000L, clock.blackMillis)
    }

    @Test
    fun `a player who runs out of time is flagged, and their clock stops at zero`() {
        val clock = ChessClock(fiveMinutes).afterMove(PieceColor.BLACK).tick(300_001) // White's clock running
        assertEquals(PieceColor.WHITE, clock.flagged)
        assertEquals(0L, clock.whiteMillis)
        assertEquals("a flagged clock doesn't keep ticking", clock, clock.tick(5_000))
    }

    @Test
    fun `unlimited never times out`() {
        var clock = ChessClock(unlimited)
        repeat(10) { i ->
            clock = clock.afterMove(if (i % 2 == 0) PieceColor.WHITE else PieceColor.BLACK).tick(1_000_000_000)
        }
        assertNull(clock.flagged)
        assertNull("no clock runs at all", clock.running)
    }

    @Test
    fun `mating material - a lone king or a lone minor against a bare king can't mate`() {
        fun fen(text: String) = requireNotNull(ChessFen.fromFen(text))
        assertTrue(!ChessEngine.hasMatingMaterial(fen("4k3/8/8/8/8/8/8/4K2R w - - 0 1"), PieceColor.BLACK))
        assertTrue(!ChessEngine.hasMatingMaterial(fen("4k3/8/8/8/8/8/8/4KN2 w - - 0 1"), PieceColor.WHITE))
        assertTrue(ChessEngine.hasMatingMaterial(fen("4k3/8/8/8/8/8/8/4K2R w - - 0 1"), PieceColor.WHITE))
        assertTrue(ChessEngine.hasMatingMaterial(fen("4k3/8/8/8/8/8/8/3NKN2 w - - 0 1"), PieceColor.WHITE))
        // A knight against a king that still has a pawn: mate is possible, so it counts.
        assertTrue(ChessEngine.hasMatingMaterial(fen("4k3/4p3/8/8/8/8/8/4KN2 w - - 0 1"), PieceColor.WHITE))
    }
}
