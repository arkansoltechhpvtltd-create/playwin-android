package com.example.playwin.core.domain.chess

/** Pure data model for a chess game - no Android or Compose dependencies. */
enum class PieceType { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING }

enum class PieceColor {
    WHITE, BLACK;

    fun opposite(): PieceColor = if (this == WHITE) BLACK else WHITE
}

data class ChessPiece(val type: PieceType, val color: PieceColor)

/** A board square; row 0 = rank 1 (White's home rank), col 0 = the a-file. */
data class Square(val row: Int, val col: Int) {
    val isOnBoard: Boolean get() = row in 0..7 && col in 0..7
}

/**
 * A single move. [promotion] is the piece a pawn becomes on reaching the last
 * rank - one of [ChessEngine.PROMOTION_PIECES] - and null for every other move.
 */
data class ChessMove(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
    val isCastle: Boolean = false,
    val isEnPassant: Boolean = false
)

/** Where a game stands. Every status but [IN_PROGRESS] ends it; all but [CHECKMATE] are draws. */
enum class GameStatus {
    IN_PROGRESS,
    CHECKMATE,
    STALEMATE,

    /** Neither side has the pieces left to ever give checkmate. */
    INSUFFICIENT_MATERIAL,

    /** 50 moves each with no capture or pawn move. */
    FIFTY_MOVE_RULE;

    val isDraw: Boolean get() = this == STALEMATE || this == INSUFFICIENT_MATERIAL || this == FIFTY_MOVE_RULE
}

/** An immutable snapshot of a game: the board plus everything needed to generate legal moves. */
data class ChessPosition(
    val board: List<List<ChessPiece?>>,
    val sideToMove: PieceColor,
    val whiteCanCastleKingSide: Boolean = true,
    val whiteCanCastleQueenSide: Boolean = true,
    val blackCanCastleKingSide: Boolean = true,
    val blackCanCastleQueenSide: Boolean = true,
    val enPassantTarget: Square? = null,
    /** Half-moves since the last capture or pawn move, for the fifty-move rule. */
    val halfmoveClock: Int = 0
) {
    fun pieceAt(square: Square): ChessPiece? = board[square.row][square.col]
}
