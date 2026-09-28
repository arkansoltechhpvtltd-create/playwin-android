package com.example.playwin.feature.game.rummy

import com.example.playwin.core.domain.rummy.Card
import com.example.playwin.core.domain.rummy.RummyEngine
import com.example.playwin.core.domain.rummy.RummyPhase
import com.example.playwin.core.domain.rummy.RummyState
import com.example.playwin.core.domain.rummy.RummyTurn

data class RummyUiState(
    val game: RummyState = RummyEngine.deal(),
    val selectedCard: Card? = null,
    val hasWon: Boolean = false,
    /** The human lost by declaring an invalid hand. */
    val hasLost: Boolean = false,
    /** The computer declared a valid hand first. */
    val computerDeclared: Boolean = false,
    val isResolving: Boolean = false,
    val isBotThinking: Boolean = false,
    val wonCouponId: String? = null
) {
    val isGameOver: Boolean get() = hasWon || hasLost || computerDeclared

    val isPlayerTurn: Boolean get() = game.turn == RummyTurn.PLAYER
    val canDraw: Boolean get() = !isGameOver && !isResolving && isPlayerTurn && game.phase == RummyPhase.AWAITING_DRAW
    val canDiscard: Boolean get() = !isGameOver && !isResolving && isPlayerTurn && game.phase == RummyPhase.AWAITING_DISCARD
    val canConfirmDiscard: Boolean get() = canDiscard && selectedCard != null
    val canDeclare: Boolean get() = canDiscard && selectedCard != null
}
