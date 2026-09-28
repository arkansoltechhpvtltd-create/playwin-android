package com.example.playwin.core.domain.rummy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RummyBotTest {

    private var nextId = 0
    private fun c(suit: Suit, rank: Rank): Card = Card.Standard(nextId++, suit, rank)
    private fun joker(): Card = Card.PrintedJoker(nextId++)

    // Wild rank for every test: SEVEN.
    private val wild = Rank.SEVEN

    /** A valid 13-card declaration: pure sequence, pure sequence, set, set of four. */
    private fun winningThirteen(): List<Card> = listOf(
        c(Suit.SPADES, Rank.TWO), c(Suit.SPADES, Rank.THREE), c(Suit.SPADES, Rank.FOUR),
        c(Suit.HEARTS, Rank.NINE), c(Suit.HEARTS, Rank.TEN), c(Suit.HEARTS, Rank.JACK),
        c(Suit.HEARTS, Rank.FIVE), c(Suit.CLUBS, Rank.FIVE), c(Suit.DIAMONDS, Rank.FIVE),
        c(Suit.SPADES, Rank.KING), c(Suit.HEARTS, Rank.KING), c(Suit.CLUBS, Rank.KING), c(Suit.DIAMONDS, Rank.KING)
    )

    @Test
    fun `a complete hand has zero deadwood`() {
        assertEquals(0, RummyBot.deadwood(winningThirteen(), wild))
    }

    @Test
    fun `a hand with no pure sequence is penalised`() {
        // Two sets and a wild-assisted run, but nothing pure.
        val hand = listOf(
            c(Suit.HEARTS, Rank.FIVE), c(Suit.CLUBS, Rank.FIVE), c(Suit.DIAMONDS, Rank.FIVE),
            c(Suit.SPADES, Rank.KING), c(Suit.HEARTS, Rank.KING), c(Suit.CLUBS, Rank.KING),
            c(Suit.SPADES, Rank.TWO), c(Suit.SPADES, Rank.THREE), joker()
        )
        assertTrue(RummyBot.deadwood(hand, wild) >= 40)
    }

    @Test
    fun `declares when one card is all that stands between it and a valid hand`() {
        val junk = c(Suit.CLUBS, Rank.QUEEN)
        val choice = RummyBot.chooseDiscard(winningThirteen() + junk, wild)
        assertTrue(choice is RummyBot.Discard.Declare)
        assertEquals(junk.id, choice.card.id)
    }

    @Test
    fun `throws the isolated high card, keeping connected ones`() {
        val loneKing = c(Suit.DIAMONDS, Rank.KING)
        val hand = listOf(
            c(Suit.SPADES, Rank.TWO), c(Suit.SPADES, Rank.THREE), c(Suit.SPADES, Rank.FOUR),
            c(Suit.HEARTS, Rank.NINE), c(Suit.HEARTS, Rank.TEN), // one away from a run
            c(Suit.CLUBS, Rank.SIX), c(Suit.DIAMONDS, Rank.SIX), // one away from a set
            c(Suit.CLUBS, Rank.TWO), c(Suit.HEARTS, Rank.THREE), c(Suit.DIAMONDS, Rank.FOUR),
            c(Suit.CLUBS, Rank.EIGHT), c(Suit.DIAMONDS, Rank.NINE), c(Suit.HEARTS, Rank.ACE),
            loneKing
        )
        val choice = RummyBot.chooseDiscard(hand, wild)
        assertTrue(choice is RummyBot.Discard.Play)
        val kept = hand.filterNot { it.id == choice.card.id }
        assertTrue("run cards kept", kept.any { it is Card.Standard && it.rank == Rank.NINE && it.suit == Suit.HEARTS })
        assertTrue("pure sequence kept", kept.count { it is Card.Standard && it.suit == Suit.SPADES } == 3)
        assertEquals(10, RummyBot.points(choice.card, wild))
    }

    @Test
    fun `never throws away a wildcard`() {
        val hand = listOf(
            c(Suit.SPADES, Rank.TWO), c(Suit.SPADES, Rank.THREE), c(Suit.SPADES, Rank.FOUR),
            joker(), c(Suit.HEARTS, wild), c(Suit.CLUBS, wild),
            c(Suit.HEARTS, Rank.KING), c(Suit.CLUBS, Rank.QUEEN), c(Suit.DIAMONDS, Rank.JACK),
            c(Suit.HEARTS, Rank.TWO), c(Suit.CLUBS, Rank.FOUR), c(Suit.DIAMONDS, Rank.SIX),
            c(Suit.HEARTS, Rank.NINE), c(Suit.CLUBS, Rank.TEN)
        )
        val choice = RummyBot.chooseDiscard(hand, wild)
        assertFalse(isWild(choice.card, wild))
    }

    @Test
    fun `takes a discard that completes a run, ignores one that doesn't help, always takes a wildcard`() {
        val hand = winningThirteen().take(12) + c(Suit.CLUBS, Rank.QUEEN) // missing the fourth king
        assertTrue(RummyBot.shouldTakeDiscard(hand, c(Suit.DIAMONDS, Rank.KING), wild))
        // A second king of clubs can't join the set (same suit) - no better than the queen already held.
        assertFalse(RummyBot.shouldTakeDiscard(hand, c(Suit.CLUBS, Rank.KING), wild))
        assertTrue(RummyBot.shouldTakeDiscard(hand, joker(), wild))
        assertFalse(RummyBot.shouldTakeDiscard(hand, null, wild))
    }

    @Test
    fun `playTurn draws the winning discard and declares`() {
        val hand = winningThirteen().take(12) + c(Suit.CLUBS, Rank.QUEEN)
        val state = RummyState(
            shoe = listOf(c(Suit.CLUBS, Rank.THREE)),
            discardPile = listOf(c(Suit.DIAMONDS, Rank.KING)),
            playerHand = emptyList(),
            botHand = hand,
            wildRank = wild,
            turn = RummyTurn.BOT,
            phase = RummyPhase.AWAITING_DRAW
        )
        val turn = RummyBot.playTurn(state)
        assertTrue(turn is RummyBot.Turn.Declared)
        assertTrue(RummyValidator.isValidDeclaration((turn as RummyBot.Turn.Declared).declaredHand, wild))
    }

    @Test
    fun `plays a full turn on real deals without breaking the rules`() {
        repeat(10) {
            val dealt = RummyEngine.deal()
            val state = dealt.copy(turn = RummyTurn.BOT)
            when (val turn = RummyBot.playTurn(state)) {
                is RummyBot.Turn.Played -> {
                    assertEquals(RummyEngine.HAND_SIZE, turn.state.botHand.size)
                    assertEquals(RummyTurn.PLAYER, turn.state.turn)
                }
                is RummyBot.Turn.Declared -> assertTrue(RummyValidator.isValidDeclaration(turn.declaredHand, state.wildRank))
            }
        }
    }
}
