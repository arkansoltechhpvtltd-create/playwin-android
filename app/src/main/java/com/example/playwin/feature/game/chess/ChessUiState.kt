package com.example.playwin.feature.game.chess

import com.example.playwin.core.domain.chess.ChessEngine
import com.example.playwin.core.domain.chess.ChessMove
import com.example.playwin.core.domain.chess.ChessPosition
import com.example.playwin.core.domain.chess.GameStatus
import com.example.playwin.core.domain.chess.PieceColor
import com.example.playwin.core.domain.chess.PieceType
import com.example.playwin.core.domain.chess.Square

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
    val promotion: PendingPromotion? = null
) {
    /** 1 = White (the human, in vs-Computer play), 2 = Black. */
    val currentPlayer: Int get() = if (position.sideToMove == PieceColor.WHITE) 1 else 2
    val isInCheck: Boolean get() = ChessEngine.isInCheck(position, position.sideToMove)
    val legalTargets: List<Square> get() = pendingMoves.map { it.to }
    val isHumanTurn: Boolean get() = position.sideToMove == CHESS_HUMAN
    val isGameOver: Boolean get() = winner != null || isDraw
}

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
