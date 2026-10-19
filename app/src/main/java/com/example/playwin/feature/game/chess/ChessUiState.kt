package com.example.playwin.feature.game.chess

import com.example.playwin.core.domain.chess.ChessClock
import com.example.playwin.core.domain.chess.ChessDifficulty
import com.example.playwin.core.domain.chess.DEFAULT_CHESS_DIFFICULTY
import com.example.playwin.core.domain.chess.ChessEngine
import com.example.playwin.core.domain.chess.ChessMove
import com.example.playwin.core.domain.chess.ChessPiece
import com.example.playwin.core.domain.chess.ChessPosition
import com.example.playwin.core.domain.chess.GameStatus
import com.example.playwin.core.domain.chess.PieceColor
import com.example.playwin.core.domain.chess.PieceType
import com.example.playwin.core.domain.chess.Square
import com.example.playwin.core.domain.chess.DEFAULT_TIME_CONTROL

data class ChessUiState(
    val position: ChessPosition = ChessEngine.initialPosition(),
    /** [ChessEngine.repetitionKey] of every position before [position], oldest first. */
    val earlierPositions: List<String> = emptyList(),
    val selectedSquare: Square? = null,
    val pendingMoves: List<ChessMove> = emptyList(),
    val winner: Int? = null,
    val isDraw: Boolean = false,
    /** Why the game was drawn, for the banner; set alongside [isDraw]. */
    val drawReason: GameStatus? = null,
    val isResolving: Boolean = false,
    val isComputerThinking: Boolean = false,
    val wonCouponId: String? = null,
    /** A pawn move onto the last rank, held until the player picks what it becomes. */
    val promotion: PendingPromotion? = null,
    /** Every move played so far, oldest first, for the move list and captured pieces. */
    val moveHistory: List<PlayedMove> = emptyList(),
    /** Both sides' clocks, and whose is running. */
    val clock: ChessClock = ChessClock(DEFAULT_TIME_CONTROL),
    /** The side whose clock ran out, when that ended the game. */
    val timedOut: PieceColor? = null,
    /** How strongly the computer plays. */
    val difficulty: ChessDifficulty = DEFAULT_CHESS_DIFFICULTY
) {
    /** The time control and difficulty can be changed until the first move is played. */
    val canChooseTimeControl: Boolean get() = moveHistory.isEmpty() && !isGameOver

    /** Black's pieces White has taken, in the order they fell. */
    val capturedByWhite: List<ChessPiece> get() = moveHistory.filter { it.mover == PieceColor.WHITE }.mapNotNull { it.captured }

    /** White's pieces Black has taken, in the order they fell. */
    val capturedByBlack: List<ChessPiece> get() = moveHistory.filter { it.mover == PieceColor.BLACK }.mapNotNull { it.captured }

    /** 1 = White (the human, in vs-Computer play), 2 = Black. */
    val currentPlayer: Int get() = if (position.sideToMove == PieceColor.WHITE) 1 else 2
    val isInCheck: Boolean get() = ChessEngine.isInCheck(position, position.sideToMove)
    val legalTargets: List<Square> get() = pendingMoves.map { it.to }
    val isHumanTurn: Boolean get() = position.sideToMove == CHESS_HUMAN
    val isGameOver: Boolean get() = winner != null || isDraw
}

/** One move as played: its [san] (standard algebraic notation), who played it, and what it took. */
data class PlayedMove(val san: String, val mover: PieceColor, val captured: ChessPiece?)

/** In "Play vs Computer" the human plays White and moves first. */
val CHESS_HUMAN = PieceColor.WHITE

/**
 * A pawn move onto the last rank that's waiting on the player's choice of
 * piece. Nothing has been played yet: picking [options] plays it (through
 * [ChessEngine.legalMove]), backing out leaves the board as it was.
 */
data class PendingPromotion(val from: Square, val to: Square, val color: PieceColor, val options: List<PieceType>)

/**
 * The promotion a tap onto [to] starts, if [moves] (the selected piece's legal
 * moves) promote there; null for an ordinary move.
 */
fun promotionFor(position: ChessPosition, moves: List<ChessMove>, to: Square): PendingPromotion? {
    val promotions = moves.filter { it.to == to && it.promotion != null }
    val first = promotions.firstOrNull() ?: return null
    return PendingPromotion(first.from, to, position.sideToMove, promotions.mapNotNull { it.promotion })
}

/** The banner for a drawn game. */
fun drawBannerText(reason: GameStatus?): String = when (reason) {
    GameStatus.INSUFFICIENT_MATERIAL -> "Draw - neither side can checkmate"
    GameStatus.FIFTY_MOVE_RULE -> "Draw - 50 moves without a capture or pawn move"
    GameStatus.THREEFOLD_REPETITION -> "Draw - the same position three times"
    else -> "Stalemate - it's a draw!"
}
