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
    val wonCouponId: String? = null
) {
    /** 1 = White, 2 = Black, matching the numbered-player convention every other hotseat game uses. */
    val currentPlayer: Int get() = if (position.sideToMove == PieceColor.WHITE) 1 else 2
    val isInCheck: Boolean get() = ChessEngine.isInCheck(position, position.sideToMove)
    val legalTargets: List<Square> get() = pendingMoves.map { it.to }
}
