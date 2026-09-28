package com.example.playwin.core.domain.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChessFenTest {

    @Test
    fun `the start position has the standard FEN`() {
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            ChessFen.toFen(ChessEngine.initialPosition())
        )
    }

    @Test
    fun `positions round-trip, including en passant and lost castling rights`() {
        var position = ChessEngine.initialPosition()
        // 1. e4 - sets the e3 en passant target.
        position = ChessEngine.applyMove(position, ChessEngine.legalMovesFrom(position, Square(1, 4)).first { it.to == Square(3, 4) })
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", ChessFen.toFen(position))
        assertEquals(position, ChessFen.fromFen(ChessFen.toFen(position)))

        val noCastling = position.copy(whiteCanCastleKingSide = false, blackCanCastleQueenSide = false)
        assertEquals(noCastling, ChessFen.fromFen(ChessFen.toFen(noCastling)))
    }

    @Test
    fun `malformed FEN is rejected`() {
        assertNull(ChessFen.fromFen("not a position"))
        assertNull(ChessFen.fromFen("8/8/8 w - - 0 1"))
    }
}
