package com.example.playwin.core.domain.chess

import org.junit.Assert.assertEquals
import org.junit.Test

class ChessNotationTest {

    private fun fen(text: String) = requireNotNull(ChessFen.fromFen(text))

    private fun sq(name: String) = Square(name[1].digitToInt() - 1, name[0] - 'a')

    /** SAN of the legal move [from]-[to] in [position]. */
    private fun san(position: ChessPosition, from: String, to: String, promotion: PieceType? = null): String {
        val move = requireNotNull(ChessEngine.legalMove(position, sq(from), sq(to), promotion)) { "$from-$to isn't legal" }
        return ChessNotation.san(position, move)
    }

    /** Plays [moves] (each "e2e4") from the start and returns the SAN of each. */
    private fun game(vararg moves: String): List<String> {
        var position = ChessEngine.initialPosition()
        return moves.map { m ->
            val move = requireNotNull(ChessEngine.legalMove(position, sq(m.substring(0, 2)), sq(m.substring(2, 4))))
            ChessNotation.san(position, move).also { position = ChessEngine.applyMove(position, move) }
        }
    }

    @Test
    fun `pawn and piece moves and captures`() {
        assertEquals(listOf("e4", "d5", "exd5", "Qxd5", "Nc3"), game("e2e4", "d7d5", "e4d5", "d8d5", "b1c3"))
    }

    @Test
    fun `check and checkmate are marked`() {
        // Fool's mate.
        assertEquals(listOf("f3", "e5", "g4", "Qh4#"), game("f2f3", "e7e5", "g2g4", "d8h4"))
        assertEquals("Bb5+", san(fen("4k3/8/8/8/8/8/8/4KB2 w - - 0 1"), "f1", "b5"))
    }

    @Test
    fun `castling both ways`() {
        val position = fen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertEquals("O-O", san(position, "e1", "g1"))
        assertEquals("O-O-O", san(position, "e1", "c1"))
    }

    @Test
    fun `promotion names the new piece`() {
        assertEquals("a8=Q", san(fen("8/P6k/8/8/8/8/8/4K3 w - - 0 1"), "a7", "a8", PieceType.QUEEN))
        assertEquals("a8=N", san(fen("8/P6k/8/8/8/8/8/4K3 w - - 0 1"), "a7", "a8", PieceType.KNIGHT))
    }

    @Test
    fun `en passant is written as a pawn capture`() {
        assertEquals("exd6", san(fen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1"), "e5", "d6"))
    }

    @Test
    fun `two pieces that could reach the square are told apart`() {
        // Knights on b1 and f3 can both reach d2: by file.
        assertEquals("Nbd2", san(fen("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1"), "b1", "d2"))
        // Rooks on a1 and a5 can both reach a3: same file, so by rank.
        assertEquals("R1a3", san(fen("4k3/8/8/R7/8/8/8/R3K3 w - - 0 1"), "a1", "a3"))
    }

    @Test
    fun `the piece a move takes, en passant included`() {
        val ep = fen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")
        val move = requireNotNull(ChessEngine.legalMove(ep, sq("e5"), sq("d6")))
        assertEquals(ChessPiece(PieceType.PAWN, PieceColor.BLACK), ChessNotation.captured(ep, move))
        val quiet = requireNotNull(ChessEngine.legalMove(ChessEngine.initialPosition(), sq("e2"), sq("e4")))
        assertEquals(null, ChessNotation.captured(ChessEngine.initialPosition(), quiet))
    }
}
