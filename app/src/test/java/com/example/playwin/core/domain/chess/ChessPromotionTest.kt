package com.example.playwin.core.domain.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChessPromotionTest {

    private fun fen(text: String) = requireNotNull(ChessFen.fromFen(text))
    private fun sq(name: String) = Square(name[1].digitToInt() - 1, name[0] - 'a')

    /** White pawn on a7 with a8 empty; Black's king on h8. */
    private val whiteToPromote = fen("7k/P7/8/8/8/8/8/4K3 w - - 0 1")

    /** As above, but h8 is boxed in by its own pawns, so a queen or rook on a8 is mate. */
    private val promoteToMate = fen("7k/P5pp/8/8/8/8/8/K7 w - - 0 1")

    private fun promote(position: ChessPosition, from: String, to: String, piece: PieceType): ChessPosition {
        val move = requireNotNull(ChessEngine.legalMove(position, sq(from), sq(to), piece)) { "no $from-$to=$piece" }
        return ChessEngine.applyMove(position, move)
    }

    @Test
    fun `a pawn reaching the last rank can become a queen, rook, bishop or knight`() {
        val promotions = ChessEngine.legalMovesFrom(whiteToPromote, sq("a7")).filter { it.to == sq("a8") }
        assertEquals(ChessEngine.PROMOTION_PIECES, promotions.map { it.promotion })
    }

    @Test
    fun `promoting puts the chosen piece on the square, for each of the four pieces`() {
        for (piece in ChessEngine.PROMOTION_PIECES) {
            val after = promote(whiteToPromote, "a7", "a8", piece)
            assertEquals(piece.name, ChessPiece(piece, PieceColor.WHITE), after.pieceAt(sq("a8")))
            assertNull(piece.name, after.pieceAt(sq("a7")))
            assertEquals(PieceColor.BLACK, after.sideToMove)
        }
    }

    @Test
    fun `black promotes on rank 1, including by capturing`() {
        val position = fen("4k3/8/8/8/8/8/1p6/R3K3 b - - 0 1")
        val captures = ChessEngine.legalMovesFrom(position, sq("b2")).filter { it.to == sq("a1") }
        assertEquals(ChessEngine.PROMOTION_PIECES, captures.map { it.promotion })
        for (piece in ChessEngine.PROMOTION_PIECES) {
            val after = promote(position, "b2", "a1", piece)
            assertEquals(ChessPiece(piece, PieceColor.BLACK), after.pieceAt(sq("a1")))
        }
    }

    @Test
    fun `a promotion must name its piece, and an ordinary move must not`() {
        assertNull(ChessEngine.legalMove(whiteToPromote, sq("a7"), sq("a8")))
        assertNull(ChessEngine.legalMove(whiteToPromote, sq("a7"), sq("a8"), PieceType.KING))
        assertNull(ChessEngine.legalMove(whiteToPromote, sq("a7"), sq("a8"), PieceType.PAWN))
        assertNull(ChessEngine.legalMove(whiteToPromote, sq("e1"), sq("e2"), PieceType.QUEEN))
        assertNotNull(ChessEngine.legalMove(whiteToPromote, sq("e1"), sq("e2")))
    }

    @Test
    fun `checkmate is detected after the promotion is applied`() {
        assertEquals(GameStatus.CHECKMATE, ChessEngine.gameStatus(promote(promoteToMate, "a7", "a8", PieceType.QUEEN)))
        assertEquals(GameStatus.CHECKMATE, ChessEngine.gameStatus(promote(promoteToMate, "a7", "a8", PieceType.ROOK)))
        // The same square, but a bishop or knight there doesn't even give check.
        assertEquals(GameStatus.IN_PROGRESS, ChessEngine.gameStatus(promote(promoteToMate, "a7", "a8", PieceType.BISHOP)))
        assertEquals(GameStatus.IN_PROGRESS, ChessEngine.gameStatus(promote(promoteToMate, "a7", "a8", PieceType.KNIGHT)))
    }

    @Test
    fun `check is detected after the promotion is applied`() {
        val after = promote(whiteToPromote, "a7", "a8", PieceType.QUEEN)
        assertTrue(ChessEngine.isInCheck(after, PieceColor.BLACK))
        val afterKnight = promote(whiteToPromote, "a7", "a8", PieceType.KNIGHT)
        assertFalse(ChessEngine.isInCheck(afterKnight, PieceColor.BLACK))
    }

    @Test
    fun `the computer promotes on its own, to a queen when that's best`() {
        val position = fen("8/P7/8/7k/8/8/8/4K3 w - - 0 1")
        repeat(5) {
            val move = requireNotNull(ChessBot.chooseMove(position, Random(it)))
            assertEquals(sq("a8"), move.to)
            assertEquals(PieceType.QUEEN, move.promotion)
        }
    }

    @Test
    fun `the computer finds mate by promotion`() {
        val move = requireNotNull(ChessBot.chooseMove(promoteToMate, Random(3)))
        assertEquals(GameStatus.CHECKMATE, ChessEngine.gameStatus(ChessEngine.applyMove(promoteToMate, move)))
    }
}
