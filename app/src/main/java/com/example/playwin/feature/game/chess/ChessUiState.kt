package com.example.playwin.feature.game.chess

import com.example.playwin.core.domain.chess.ChessEngine
import com.example.playwin.core.domain.chess.ChessMove
import com.example.playwin.core.domain.chess.ChessPosition
import com.example.playwin.core.domain.chess.PieceColor
import com.example.playwin.core.domain.chess.Square

data class ChessUiState(
    val position: ChessPosition = ChessEngine.initialPosition(),
    val selectedSquare: Square? = null,
    val pendingMoves: List<ChessMove> = emptyList(),
    val winner: Int? = null,
    val isDraw: Boolean = false,
    val isResolving: Boolean = false,
    val isComputerThinking: Boolean = false,
    val wonCouponId: String? = null
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
