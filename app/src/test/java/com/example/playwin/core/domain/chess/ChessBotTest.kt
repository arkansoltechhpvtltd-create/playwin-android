package com.example.playwin.core.domain.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChessBotTest {

    private fun fen(text: String) = requireNotNull(ChessFen.fromFen(text))
    private fun sq(name: String) = Square(name[1].digitToInt() - 1, name[0] - 'a')

    @Test
    fun `plays mate in one`() {
        // Back-rank mate: Ra1-a8#.
        val position = fen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1")
        repeat(10) {
            val move = assertNotNullMove(ChessBot.chooseMove(position, Random(it)))
            assertEquals(sq("a1"), move.from)
            assertEquals(sq("a8"), move.to)
        }
    }

    @Test
    fun `captures a free queen`() {
        val position = fen("q3k3/8/8/8/8/8/8/R3K3 w - - 0 1")
        val move = assertNotNullMove(ChessBot.chooseMove(position, Random(1)))
        assertEquals(sq("a8"), move.to)
    }

    @Test
    fun `won't trade its queen for a defended pawn`() {
        // Qd1xd5 wins a pawn but e6xd5 wins the queen back.
        val position = fen("4k3/8/4p3/3p4/8/8/8/3QK3 w - - 0 1")
        repeat(10) {
            val move = assertNotNullMove(ChessBot.chooseMove(position, Random(it)))
            assertNotEquals(sq("d5"), move.to)
        }
    }

    @Test
    fun `moves its queen out of attack rather than leaving it hanging`() {
        // Black's knight on c6 attacks the white queen on d4 (undefended).
        val position = fen("4k3/8/2n5/8/3Q4/8/8/4K3 w - - 0 1")
        val move = assertNotNullMove(ChessBot.chooseMove(position, Random(3)))
        assertEquals(sq("d4"), move.from)
    }

    @Test
    fun `prefers giving check when nothing can be captured`() {
        val position = fen("4k3/8/8/8/8/8/8/R3K3 w - - 0 1")
        val move = assertNotNullMove(ChessBot.chooseMove(position, Random(5)))
        assertEquals(sq("a8"), move.to)
    }

    @Test
    fun `has no move when checkmated`() {
        val mated = fen("R5k1/5ppp/8/8/8/8/8/6K1 b - - 0 1")
        assertEquals(GameStatus.CHECKMATE, ChessEngine.gameStatus(mated))
        assertNull(ChessBot.chooseMove(mated))
    }

    @Test
    fun `only ever plays legal moves`() {
        var position = ChessEngine.initialPosition()
        repeat(40) { ply ->
            if (ChessEngine.gameStatus(position) != GameStatus.IN_PROGRESS) return
            val move = assertNotNullMove(ChessBot.chooseMove(position, Random(ply)))
            assertTrue(move in ChessEngine.legalMoves(position))
            position = ChessEngine.applyMove(position, move)
        }
    }

    private fun assertNotNullMove(move: ChessMove?): ChessMove {
        assertNotNull(move)
        return move!!
    }
}
