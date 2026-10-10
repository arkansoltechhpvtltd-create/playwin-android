package com.example.playwin.feature.game.rummy

import com.example.playwin.core.domain.rummy.Card
import com.example.playwin.core.domain.rummy.Rank
import com.example.playwin.core.domain.rummy.RummyEngine
import com.example.playwin.core.domain.rummy.RummyPhase
import com.example.playwin.core.domain.rummy.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandArrangementTest {

    private val sevenH = Card.Standard(1, Suit.HEARTS, Rank.SEVEN)
    private val twoS = Card.Standard(2, Suit.SPADES, Rank.TWO)
    private val joker = Card.PrintedJoker(3)
    private val kingS = Card.Standard(4, Suit.SPADES, Rank.KING)

    @Test
    fun `default arrangement groups by suit then rank, jokers last`() {
        assertEquals(listOf(twoS, kingS, sevenH, joker), HandArrangement.sorted(listOf(joker, sevenH, kingS, twoS)))
    }

    @Test
    fun `move shifts the cards in between`() {
        assertEquals(listOf(2, 3, 1, 4), HandArrangement.move(listOf(1, 2, 3, 4), from = 0, to = 2))
        assertEquals(listOf(4, 1, 2, 3), HandArrangement.move(listOf(1, 2, 3, 4), from = 3, to = 0))
        assertEquals(listOf(1, 2, 3), HandArrangement.move(listOf(1, 2, 3), from = 0, to = 5))
    }

    @Test
    fun `arrange keeps the player's order, drops played cards and appends new draws`() {
        val hand = listOf(twoS, sevenH, joker, kingS)
        assertEquals(listOf(kingS, twoS, sevenH, joker), HandArrangement.arrange(hand, order = listOf(4, 2, 99)))
    }

    @Test
    fun `reordering changes only the display order, never the cards held`() {
        val state = RummyUiState()
        val reordered = state.copy(handOrder = HandArrangement.move(state.arrangedHand.map { it.id }, 0, 12))

        assertEquals(state.game, reordered.game)
        assertEquals(state.arrangedHand.drop(1) + state.arrangedHand.first(), reordered.arrangedHand)
        assertEquals(state.game.playerHand.toSet(), reordered.arrangedHand.toSet())
    }

    @Test
    fun `arrangement survives drawing and discarding`() {
        val start = RummyUiState()
        val arranged = start.copy(handOrder = start.arrangedHand.map { it.id }.reversed())

        val drawn = RummyEngine.drawFromShoe(arranged.game)
        val afterDraw = arranged.copy(game = drawn)
        val newCard = drawn.playerHand.last()
        assertEquals(arranged.arrangedHand + newCard, afterDraw.arrangedHand)

        val thrown = afterDraw.arrangedHand[3]
        val afterDiscard = afterDraw.copy(game = RummyEngine.discard(drawn, thrown))
        assertEquals(afterDraw.arrangedHand - thrown, afterDiscard.arrangedHand)
        assertEquals(RummyPhase.AWAITING_DRAW, afterDiscard.game.phase)
    }

    @Test
    fun `computer's hand is hidden mid-game and revealed once the round ends`() {
        val playing = RummyUiState()
        assertNull(playing.revealedOpponentHand)

        val lost = playing.copy(hasLost = true)
        assertEquals(playing.game.botHand.toSet(), lost.revealedOpponentHand!!.toSet())

        val declared = playing.game.botHand.take(13)
        val computerWon = playing.copy(computerDeclared = true, computerDeclaredHand = declared)
        assertEquals(declared.toSet(), computerWon.revealedOpponentHand!!.toSet())
    }

    @Test
    fun `nearest slot snaps to the grid and stays within the hand`() {
        val grid = HandGrid(perRow = 6, stepX = 100f, stepY = 200f)
        assertEquals(0, grid.nearestSlot(-40f, -80f, count = 13))
        assertEquals(7, grid.nearestSlot(120f, 190f, count = 13))
        assertEquals(12, grid.nearestSlot(560f, 420f, count = 13)) // past the last card in the last row
        assertEquals(12, grid.nearestSlot(0f, 900f, count = 13))
    }
}
