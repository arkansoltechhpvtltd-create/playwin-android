package com.example.playwin.core.domain.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessEngineTest {

    private fun emptyBoard(): MutableList<MutableList<ChessPiece?>> =
        MutableList(8) { MutableList<ChessPiece?>(8) { null } }

    private fun customPosition(
        pieces: Map<Square, ChessPiece>,
        sideToMove: PieceColor,
        whiteKingSide: Boolean = false,
        whiteQueenSide: Boolean = false,
        blackKingSide: Boolean = false,
        blackQueenSide: Boolean = false,
        enPassantTarget: Square? = null
    ): ChessPosition {
        val board = emptyBoard()
        for ((square, piece) in pieces) board[square.row][square.col] = piece
        return ChessPosition(
            board = board,
            sideToMove = sideToMove,
            whiteCanCastleKingSide = whiteKingSide,
            whiteCanCastleQueenSide = whiteQueenSide,
            blackCanCastleKingSide = blackKingSide,
            blackCanCastleQueenSide = blackQueenSide,
            enPassantTarget = enPassantTarget
        )
    }

    @Test
    fun `start position has exactly 20 legal moves`() {
        val position = ChessEngine.initialPosition()
        assertEquals(20, ChessEngine.legalMoves(position).size)
    }

    @Test
    fun `fools mate is detected as checkmate`() {
        var position = ChessEngine.initialPosition()
        val plies = listOf(
            Square(1, 5) to Square(2, 5), // 1. f3
            Square(6, 4) to Square(4, 4), // e5
            Square(1, 6) to Square(3, 6), // 2. g4
            Square(7, 3) to Square(3, 7)  // Qh4#
        )
        for ((from, to) in plies) {
            val move = ChessEngine.legalMovesFrom(position, from).first { it.to == to }
            position = ChessEngine.applyMove(position, move)
        }
        assertEquals(GameStatus.CHECKMATE, ChessEngine.gameStatus(position))
    }

    @Test
    fun `castling is offered when the path is clear and not through check`() {
        val position = customPosition(
            pieces = mapOf(
                Square(0, 4) to ChessPiece(PieceType.KING, PieceColor.WHITE),
                Square(0, 7) to ChessPiece(PieceType.ROOK, PieceColor.WHITE),
                Square(7, 4) to ChessPiece(PieceType.KING, PieceColor.BLACK)
            ),
            sideToMove = PieceColor.WHITE,
            whiteKingSide = true
        )

        val moves = ChessEngine.legalMoves(position)

        assertTrue(moves.any { it.from == Square(0, 4) && it.to == Square(0, 6) && it.isCastle })
    }

    @Test
    fun `castling is not offered through an attacked square`() {
        val position = customPosition(
            pieces = mapOf(
                Square(0, 4) to ChessPiece(PieceType.KING, PieceColor.WHITE),
                Square(0, 7) to ChessPiece(PieceType.ROOK, PieceColor.WHITE),
                Square(7, 4) to ChessPiece(PieceType.KING, PieceColor.BLACK),
                // Black rook on the f-file attacks f1 - a square the king must pass through.
                Square(5, 5) to ChessPiece(PieceType.ROOK, PieceColor.BLACK)
            ),
            sideToMove = PieceColor.WHITE,
            whiteKingSide = true
        )

        val moves = ChessEngine.legalMoves(position)

        assertFalse(moves.any { it.isCastle })
    }

    @Test
    fun `en passant is available immediately after an adjacent pawn double-steps`() {
        val position = customPosition(
            pieces = mapOf(
                Square(0, 4) to ChessPiece(PieceType.KING, PieceColor.WHITE),
                Square(4, 4) to ChessPiece(PieceType.PAWN, PieceColor.WHITE), // e5
                Square(6, 3) to ChessPiece(PieceType.PAWN, PieceColor.BLACK), // d7
                Square(7, 4) to ChessPiece(PieceType.KING, PieceColor.BLACK)
            ),
            sideToMove = PieceColor.BLACK
        )

        val doubleStep = ChessEngine.legalMovesFrom(position, Square(6, 3)).first { it.to == Square(4, 3) }
        val afterDoubleStep = ChessEngine.applyMove(position, doubleStep)

        assertEquals(Square(5, 3), afterDoubleStep.enPassantTarget)
        val whitePawnMoves = ChessEngine.legalMovesFrom(afterDoubleStep, Square(4, 4))
        assertTrue(whitePawnMoves.any { it.to == Square(5, 3) && it.isEnPassant })
    }

    @Test
    fun `a pinned piece has no legal moves`() {
        val position = customPosition(
            pieces = mapOf(
                Square(0, 4) to ChessPiece(PieceType.KING, PieceColor.WHITE),
                Square(1, 4) to ChessPiece(PieceType.BISHOP, PieceColor.WHITE),
                Square(7, 4) to ChessPiece(PieceType.ROOK, PieceColor.BLACK),
                Square(7, 0) to ChessPiece(PieceType.KING, PieceColor.BLACK)
            ),
            sideToMove = PieceColor.WHITE
        )

        // Not currently in check - the bishop is blocking the file, which is exactly what makes it pinned.
        assertFalse(ChessEngine.isInCheck(position, PieceColor.WHITE))
        assertTrue(ChessEngine.legalMovesFrom(position, Square(1, 4)).isEmpty())
    }
}
