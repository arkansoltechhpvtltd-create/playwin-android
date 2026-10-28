package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.rummy.Card
import com.example.playwin.core.domain.rummy.Rank
import com.example.playwin.core.domain.rummy.RummyEngine
import com.example.playwin.core.domain.rummy.Suit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The engine, not the UI, rules on a declaration - the same way for the player's hand and the computer's. */
class RummyResolveTest {

    private val engine = MockGameEngineRepository()

    /** Real cards from the shoe, each handed out once - as a dealt hand would be. */
    private val shoe = RummyEngine.buildShoe().toMutableList()
    private fun c(suit: Suit, rank: Rank): Card =
        shoe.first { it is Card.Standard && it.suit == suit && it.rank == rank }.also { shoe.remove(it) }
    private fun joker(): Card = shoe.first { it is Card.PrintedJoker }.also { shoe.remove(it) }

    private fun resolve(hand: List<Card>, wildRank: Rank) =
        runBlocking { engine.resolve(GameId.RUMMY, GameSnapshot.Rummy(declaredHand = hand, wildRank = wildRank)) }

    private fun winningHand() = listOf(
        c(Suit.SPADES, Rank.QUEEN), c(Suit.SPADES, Rank.KING), c(Suit.SPADES, Rank.ACE),
        c(Suit.HEARTS, Rank.TWO), c(Suit.HEARTS, Rank.THREE), joker(),
        c(Suit.SPADES, Rank.NINE), c(Suit.HEARTS, Rank.NINE), c(Suit.CLUBS, Rank.NINE),
        c(Suit.SPADES, Rank.FIVE), c(Suit.HEARTS, Rank.FIVE), c(Suit.CLUBS, Rank.FIVE), c(Suit.DIAMONDS, Rank.FIVE)
    )

    @Test
    fun `an ace-high pure sequence plus an impure one wins`() {
        assertEquals(GameOutcome.WIN, resolve(winningHand(), Rank.SEVEN))
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

    @Test
    fun `a declaration with an invented card loses even if it would meld`() {
        val hand = winningHand().toMutableList()
        // The same id passed off as a different card than the shoe's.
        val real = hand[6] as Card.Standard
        hand[6] = real.copy(suit = Suit.DIAMONDS)
        assertEquals(GameOutcome.LOSE, resolve(hand, Rank.SEVEN))
        // A card id that isn't in the shoe at all.
        assertEquals(GameOutcome.LOSE, resolve(winningHand().dropLast(1) + Card.PrintedJoker(999), Rank.SEVEN))
    }

    @Test
    fun `a declaration holding the same card twice loses`() {
        val hand = winningHand()
        assertEquals(GameOutcome.LOSE, resolve(hand.dropLast(1) + hand.first(), Rank.SEVEN))
    }
}
