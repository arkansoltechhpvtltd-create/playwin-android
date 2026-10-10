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
    val wonCouponId: String? = null,
    /** The 13 cards the computer declared, if it declared (valid or not). */
    val computerDeclaredHand: List<Card>? = null,
    /**
     * The player's own drag-and-drop arrangement of their hand, as card ids;
     * null until they first move a card (the hand is shown sorted). Display
     * only - the cards held are always [RummyState.playerHand].
     */
    val handOrder: List<Int>? = null
) {
    val isGameOver: Boolean get() = hasWon || hasLost || computerDeclared

    /** The player's hand in the order they've arranged it on screen. */
    val arrangedHand: List<Card>
        get() = handOrder?.let { HandArrangement.arrange(game.playerHand, it) }
            ?: HandArrangement.sorted(game.playerHand)

    /**
     * The computer's cards, face up - only once the round is over, never
     * mid-game. Its declared 13 if it declared, otherwise whatever it held.
     */
    val revealedOpponentHand: List<Card>?
        get() = if (isGameOver) HandArrangement.sorted(computerDeclaredHand ?: game.botHand) else null

    val isPlayerTurn: Boolean get() = game.turn == RummyTurn.PLAYER
    val canDraw: Boolean get() = !isGameOver && !isResolving && isPlayerTurn && game.phase == RummyPhase.AWAITING_DRAW
    val canDiscard: Boolean get() = !isGameOver && !isResolving && isPlayerTurn && game.phase == RummyPhase.AWAITING_DISCARD
    val canConfirmDiscard: Boolean get() = canDiscard && selectedCard != null
    val canDeclare: Boolean get() = canDiscard && selectedCard != null
}
