package com.example.playwin.core.domain.rummy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RummyValidatorTest {

    private var nextId = 0
    private fun c(suit: Suit, rank: Rank): Card = Card.Standard(nextId++, suit, rank)
    private fun joker(): Card = Card.PrintedJoker(nextId++)

    @Test
    fun `a hand of all pure sequences is accepted`() {
        val hand = listOf(
            c(Suit.SPADES, Rank.TWO), c(Suit.SPADES, Rank.THREE), c(Suit.SPADES, Rank.FOUR),
            c(Suit.HEARTS, Rank.FIVE), c(Suit.HEARTS, Rank.SIX), c(Suit.HEARTS, Rank.SEVEN),
            c(Suit.CLUBS, Rank.EIGHT), c(Suit.CLUBS, Rank.NINE), c(Suit.CLUBS, Rank.TEN),
            c(Suit.DIAMONDS, Rank.TWO), c(Suit.DIAMONDS, Rank.THREE), c(Suit.DIAMONDS, Rank.FOUR), c(Suit.DIAMONDS, Rank.FIVE)
        )
        assertTrue(RummyValidator.isValidDeclaration(hand, wildRank = Rank.ACE))
    }

    @Test
    fun `a hand mixing a pure sequence, sets, and a wild-assisted sequence is accepted`() {
        val wildRank = Rank.SEVEN
        val hand = listOf(
            // pure sequence
            c(Suit.SPADES, Rank.TWO), c(Suit.SPADES, Rank.THREE), c(Suit.SPADES, Rank.FOUR),
            // set
            c(Suit.HEARTS, Rank.FIVE), c(Suit.CLUBS, Rank.FIVE), c(Suit.DIAMONDS, Rank.FIVE),
            // wild-assisted sequence: Hearts 8, 9, [wild-rank card standing in for Hearts 10]
            c(Suit.HEARTS, Rank.EIGHT), c(Suit.HEARTS, Rank.NINE), c(Suit.CLUBS, wildRank),
            // set
            c(Suit.SPADES, Rank.KING), c(Suit.HEARTS, Rank.KING), c(Suit.CLUBS, Rank.KING), c(Suit.DIAMONDS, Rank.KING)
        )
        assertTrue(RummyValidator.isValidDeclaration(hand, wildRank))
    }

    @Test
    fun `valid groups with no pure sequence are rejected`() {
        val hand = listOf(
            // Ranks are spaced apart (1, 3, 5, 7) so no suit can accidentally contain a 3-run.
            c(Suit.SPADES, Rank.ACE), c(Suit.HEARTS, Rank.ACE), c(Suit.CLUBS, Rank.ACE),
            c(Suit.SPADES, Rank.THREE), c(Suit.HEARTS, Rank.THREE), c(Suit.CLUBS, Rank.THREE),
            c(Suit.SPADES, Rank.FIVE), c(Suit.HEARTS, Rank.FIVE), c(Suit.CLUBS, Rank.FIVE),
            c(Suit.SPADES, Rank.SEVEN), c(Suit.HEARTS, Rank.SEVEN), c(Suit.CLUBS, Rank.SEVEN), c(Suit.DIAMONDS, Rank.SEVEN)
        )
        // All four groups are valid sets - no sequence at all, so definitely no pure sequence.
        assertFalse(RummyValidator.isValidDeclaration(hand, wildRank = Rank.KING))
    }

    @Test
    fun `a hand with a leftover ungrouped card is rejected`() {
        val hand = listOf(
            c(Suit.SPADES, Rank.TWO), c(Suit.SPADES, Rank.THREE), c(Suit.SPADES, Rank.FOUR),
            c(Suit.HEARTS, Rank.FIVE), c(Suit.HEARTS, Rank.SIX), c(Suit.HEARTS, Rank.SEVEN),
            c(Suit.CLUBS, Rank.EIGHT), c(Suit.CLUBS, Rank.NINE), c(Suit.CLUBS, Rank.TEN),
            // four cards that share no rank and no suit-run with each other or anything above
            c(Suit.DIAMONDS, Rank.ACE), c(Suit.DIAMONDS, Rank.KING), c(Suit.CLUBS, Rank.QUEEN), c(Suit.SPADES, Rank.JACK)
        )
        assertFalse(RummyValidator.isValidDeclaration(hand, wildRank = Rank.JACK))
    }

    @Test
    fun `a printed joker can substitute for a missing card in a sequence`() {
        val hand = listOf(
            // impure sequence using a printed joker to stand in for Spades 4
            c(Suit.SPADES, Rank.TWO), c(Suit.SPADES, Rank.THREE), joker(),
            // pure sequence, satisfying the at-least-one-pure requirement
            c(Suit.HEARTS, Rank.FIVE), c(Suit.HEARTS, Rank.SIX), c(Suit.HEARTS, Rank.SEVEN),
            // sets
            c(Suit.SPADES, Rank.NINE), c(Suit.HEARTS, Rank.NINE), c(Suit.CLUBS, Rank.NINE),
            c(Suit.SPADES, Rank.KING), c(Suit.HEARTS, Rank.KING), c(Suit.CLUBS, Rank.KING), c(Suit.DIAMONDS, Rank.KING)
        )
        assertTrue(RummyValidator.isValidDeclaration(hand, wildRank = Rank.ACE))
    }

    @Test
    fun `a wild-rank card can substitute for a missing card in a set`() {
        val wildRank = Rank.SIX
        val hand = listOf(
            // pure sequence
            c(Suit.SPADES, Rank.TWO), c(Suit.SPADES, Rank.THREE), c(Suit.SPADES, Rank.FOUR),
            // set of nines completed with a wild-rank card standing in for the fourth suit
            c(Suit.SPADES, Rank.NINE), c(Suit.HEARTS, Rank.NINE), c(Suit.CLUBS, Rank.NINE), c(Suit.DIAMONDS, wildRank),
            // second sequence, then a set
            c(Suit.HEARTS, Rank.JACK), c(Suit.HEARTS, Rank.QUEEN), c(Suit.HEARTS, Rank.KING),
            c(Suit.SPADES, Rank.KING), c(Suit.DIAMONDS, Rank.KING), c(Suit.CLUBS, Rank.KING)
        )
        assertTrue(RummyValidator.isValidDeclaration(hand, wildRank))
    }

    // --- Aces: low or high within a run, never both (no wrap-around) ---

    @Test
    fun `Q-K-A is a pure sequence, ace high`() {
        val run = listOf(c(Suit.SPADES, Rank.QUEEN), c(Suit.SPADES, Rank.KING), c(Suit.SPADES, Rank.ACE))
        assertEquals(GroupKind.PURE_SEQUENCE, RummyValidator.classifyGroup(run, wildRank = Rank.SEVEN))
    }

    @Test
    fun `a declaration whose only pure sequence is Q-K-A is accepted`() {
        val hand = listOf(
            c(Suit.SPADES, Rank.QUEEN), c(Suit.SPADES, Rank.KING), c(Suit.SPADES, Rank.ACE),
            c(Suit.HEARTS, Rank.TWO), c(Suit.HEARTS, Rank.THREE), joker(),
            c(Suit.SPADES, Rank.NINE), c(Suit.HEARTS, Rank.NINE), c(Suit.CLUBS, Rank.NINE),
            c(Suit.SPADES, Rank.FIVE), c(Suit.HEARTS, Rank.FIVE), c(Suit.CLUBS, Rank.FIVE), c(Suit.DIAMONDS, Rank.FIVE)
        )
        assertTrue(RummyValidator.isValidDeclaration(hand, wildRank = Rank.SEVEN))
    }

    @Test
    fun `A-2-3 is a pure sequence, ace low`() {
        val run = listOf(c(Suit.CLUBS, Rank.ACE), c(Suit.CLUBS, Rank.TWO), c(Suit.CLUBS, Rank.THREE))
        assertEquals(GroupKind.PURE_SEQUENCE, RummyValidator.classifyGroup(run, wildRank = Rank.SEVEN))
    }

    @Test
    fun `a declaration whose only pure sequence is A-2-3 is accepted`() {
        val hand = listOf(
            c(Suit.CLUBS, Rank.ACE), c(Suit.CLUBS, Rank.TWO), c(Suit.CLUBS, Rank.THREE),
            c(Suit.HEARTS, Rank.SEVEN), c(Suit.HEARTS, Rank.EIGHT), joker(),
            c(Suit.SPADES, Rank.NINE), c(Suit.HEARTS, Rank.NINE), c(Suit.CLUBS, Rank.NINE),
            c(Suit.SPADES, Rank.KING), c(Suit.HEARTS, Rank.KING), c(Suit.CLUBS, Rank.KING), c(Suit.DIAMONDS, Rank.KING)
        )
        assertTrue(RummyValidator.isValidDeclaration(hand, wildRank = Rank.FIVE))
    }

    @Test
    fun `K-A-2 does not wrap around`() {
        val run = listOf(c(Suit.SPADES, Rank.KING), c(Suit.SPADES, Rank.ACE), c(Suit.SPADES, Rank.TWO))
        assertNull(RummyValidator.classifyGroup(run, wildRank = Rank.SEVEN))
        // Not even with a joker filling in: the ace still can't be high and low at once.
        val longer = run + joker()
        assertNull(RummyValidator.classifyGroup(longer, wildRank = Rank.SEVEN))
    }

    @Test
    fun `a declaration that needs K-A-2 is rejected`() {
        val hand = listOf(
            c(Suit.SPADES, Rank.KING), c(Suit.SPADES, Rank.ACE), c(Suit.SPADES, Rank.TWO),
            c(Suit.HEARTS, Rank.SIX), c(Suit.HEARTS, Rank.SEVEN), c(Suit.HEARTS, Rank.EIGHT),
            c(Suit.CLUBS, Rank.NINE), c(Suit.CLUBS, Rank.TEN), joker(),
            c(Suit.SPADES, Rank.FOUR), c(Suit.HEARTS, Rank.FOUR), c(Suit.CLUBS, Rank.FOUR), c(Suit.DIAMONDS, Rank.FOUR)
        )
        assertFalse(RummyValidator.isValidDeclaration(hand, wildRank = Rank.JACK))
    }

    // --- Two sequences, at least one pure ---

    @Test
    fun `one pure sequence and three sets is rejected - a second sequence is needed`() {
        val hand = listOf(
            c(Suit.SPADES, Rank.TWO), c(Suit.SPADES, Rank.THREE), c(Suit.SPADES, Rank.FOUR),
            c(Suit.SPADES, Rank.NINE), c(Suit.HEARTS, Rank.NINE), c(Suit.CLUBS, Rank.NINE),
            c(Suit.SPADES, Rank.QUEEN), c(Suit.HEARTS, Rank.QUEEN), c(Suit.CLUBS, Rank.QUEEN),
            c(Suit.SPADES, Rank.KING), c(Suit.HEARTS, Rank.KING), c(Suit.CLUBS, Rank.KING), c(Suit.DIAMONDS, Rank.KING)
        )
        assertFalse(RummyValidator.isValidDeclaration(hand, wildRank = Rank.SIX))
    }

    @Test
    fun `two sequences with one pure, plus sets, is accepted`() {
        val hand = listOf(
            c(Suit.SPADES, Rank.TWO), c(Suit.SPADES, Rank.THREE), c(Suit.SPADES, Rank.FOUR),
            c(Suit.HEARTS, Rank.TEN), joker(), c(Suit.HEARTS, Rank.QUEEN),
            c(Suit.SPADES, Rank.NINE), c(Suit.HEARTS, Rank.NINE), c(Suit.CLUBS, Rank.NINE),
            c(Suit.SPADES, Rank.KING), c(Suit.HEARTS, Rank.KING), c(Suit.CLUBS, Rank.KING), c(Suit.DIAMONDS, Rank.KING)
        )
        assertTrue(RummyValidator.isValidDeclaration(hand, wildRank = Rank.SIX))
    }

    @Test
    fun `two impure sequences without a pure one are rejected`() {
        val hand = listOf(
            c(Suit.SPADES, Rank.TWO), c(Suit.SPADES, Rank.THREE), joker(),
            c(Suit.HEARTS, Rank.TEN), joker(), c(Suit.HEARTS, Rank.QUEEN),
            c(Suit.SPADES, Rank.NINE), c(Suit.HEARTS, Rank.NINE), c(Suit.CLUBS, Rank.NINE),
            c(Suit.SPADES, Rank.KING), c(Suit.HEARTS, Rank.KING), c(Suit.CLUBS, Rank.KING), c(Suit.DIAMONDS, Rank.KING)
        )
        assertFalse(RummyValidator.isValidDeclaration(hand, wildRank = Rank.SIX))
    }

    // --- What makes a run pure ---

    @Test
    fun `a wild-rank card in its natural run keeps the run pure`() {
        val run = listOf(c(Suit.HEARTS, Rank.FOUR), c(Suit.HEARTS, Rank.FIVE), c(Suit.HEARTS, Rank.SIX))
        assertEquals(GroupKind.PURE_SEQUENCE, RummyValidator.classifyGroup(run, wildRank = Rank.FIVE))
    }

    @Test
    fun `a declaration whose only pure sequence holds a natural wild-rank card is accepted`() {
        val hand = listOf(
            c(Suit.HEARTS, Rank.FOUR), c(Suit.HEARTS, Rank.FIVE), c(Suit.HEARTS, Rank.SIX),
            c(Suit.SPADES, Rank.NINE), c(Suit.SPADES, Rank.TEN), joker(),
            c(Suit.SPADES, Rank.TWO), c(Suit.HEARTS, Rank.TWO), c(Suit.CLUBS, Rank.TWO),
            c(Suit.SPADES, Rank.KING), c(Suit.HEARTS, Rank.KING), c(Suit.CLUBS, Rank.KING), c(Suit.DIAMONDS, Rank.KING)
        )
        assertTrue(RummyValidator.isValidDeclaration(hand, wildRank = Rank.FIVE))
    }

    @Test
    fun `a run using a joker as a substitute is impure`() {
        val wildRank = Rank.FIVE
        // A wild-rank card of another suit standing in for the 5 of hearts.
        val withWildCard = listOf(c(Suit.HEARTS, Rank.FOUR), c(Suit.CLUBS, Rank.FIVE), c(Suit.HEARTS, Rank.SIX))
        assertEquals(GroupKind.IMPURE_SEQUENCE, RummyValidator.classifyGroup(withWildCard, wildRank))
        // A wild-rank card standing in for a different rank.
        val wildAsSeven = listOf(c(Suit.HEARTS, Rank.EIGHT), c(Suit.HEARTS, Rank.FIVE), c(Suit.HEARTS, Rank.SIX))
        assertEquals(GroupKind.IMPURE_SEQUENCE, RummyValidator.classifyGroup(wildAsSeven, wildRank))
        // A printed joker.
        val withJoker = listOf(c(Suit.HEARTS, Rank.FOUR), joker(), c(Suit.HEARTS, Rank.SIX))
        assertEquals(GroupKind.IMPURE_SEQUENCE, RummyValidator.classifyGroup(withJoker, wildRank))
    }

    @Test
    fun `a declaration whose only complete runs use jokers is rejected`() {
        val hand = listOf(
            c(Suit.HEARTS, Rank.FOUR), joker(), c(Suit.HEARTS, Rank.SIX),
            c(Suit.SPADES, Rank.NINE), c(Suit.SPADES, Rank.TEN), c(Suit.CLUBS, Rank.FIVE),
            c(Suit.SPADES, Rank.TWO), c(Suit.HEARTS, Rank.TWO), c(Suit.CLUBS, Rank.TWO),
            c(Suit.SPADES, Rank.KING), c(Suit.HEARTS, Rank.KING), c(Suit.CLUBS, Rank.KING), c(Suit.DIAMONDS, Rank.KING)
        )
        assertFalse(RummyValidator.isValidDeclaration(hand, wildRank = Rank.FIVE))
    }

    @Test
    fun `random unstructured cards are rejected`() {
        // Every rank appears exactly once (no possible set), suits cycle every 4 ranks so no
        // 3 consecutive ranks ever share a suit (no possible sequence either).
        val suitsCycle = listOf(Suit.SPADES, Suit.HEARTS, Suit.CLUBS, Suit.DIAMONDS)
        val hand = Rank.entries.map { rank -> c(suitsCycle[(rank.order - 1) % 4], rank) }
        assertFalse(RummyValidator.isValidDeclaration(hand, wildRank = Rank.SEVEN))
    }

    @Test
    fun `a hand that is not exactly 13 cards is rejected`() {
        val hand = listOf(c(Suit.SPADES, Rank.TWO), c(Suit.SPADES, Rank.THREE), c(Suit.SPADES, Rank.FOUR))
        assertFalse(RummyValidator.isValidDeclaration(hand, wildRank = Rank.ACE))
    }
}
