package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.rummy.Card
import com.example.playwin.core.domain.rummy.Rank
import com.example.playwin.core.domain.rummy.Suit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The engine, not the UI, rules on a declaration - the same way for the player's hand and the computer's. */
class RummyResolveTest {

    private val engine = MockGameEngineRepository()
    private var nextId = 0
    private fun c(suit: Suit, rank: Rank): Card = Card.Standard(nextId++, suit, rank)
    private fun joker(): Card = Card.PrintedJoker(nextId++)

    private fun resolve(hand: List<Card>, wildRank: Rank) =
        runBlocking { engine.resolve(GameId.RUMMY, GameSnapshot.Rummy(declaredHand = hand, wildRank = wildRank)) }

    @Test
    fun `an ace-high pure sequence plus an impure one wins`() {
        val hand = listOf(
            c(Suit.SPADES, Rank.QUEEN), c(Suit.SPADES, Rank.KING), c(Suit.SPADES, Rank.ACE),
            c(Suit.HEARTS, Rank.TWO), c(Suit.HEARTS, Rank.THREE), joker(),
            c(Suit.SPADES, Rank.NINE), c(Suit.HEARTS, Rank.NINE), c(Suit.CLUBS, Rank.NINE),
            c(Suit.SPADES, Rank.FIVE), c(Suit.HEARTS, Rank.FIVE), c(Suit.CLUBS, Rank.FIVE), c(Suit.DIAMONDS, Rank.FIVE)
        )
        assertEquals(GameOutcome.WIN, resolve(hand, Rank.SEVEN))
    }

    @Test
    fun `one pure sequence and three sets loses`() {
        val hand = listOf(
            c(Suit.SPADES, Rank.TWO), c(Suit.SPADES, Rank.THREE), c(Suit.SPADES, Rank.FOUR),
            c(Suit.SPADES, Rank.NINE), c(Suit.HEARTS, Rank.NINE), c(Suit.CLUBS, Rank.NINE),
            c(Suit.SPADES, Rank.QUEEN), c(Suit.HEARTS, Rank.QUEEN), c(Suit.CLUBS, Rank.QUEEN),
            c(Suit.SPADES, Rank.KING), c(Suit.HEARTS, Rank.KING), c(Suit.CLUBS, Rank.KING), c(Suit.DIAMONDS, Rank.KING)
        )
        assertEquals(GameOutcome.LOSE, resolve(hand, Rank.SIX))
    }
}
