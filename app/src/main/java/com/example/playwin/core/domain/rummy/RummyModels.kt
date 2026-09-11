package com.example.playwin.core.domain.rummy

enum class Suit { SPADES, HEARTS, DIAMONDS, CLUBS }

enum class Rank(val order: Int) {
    ACE(1), TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7),
    EIGHT(8), NINE(9), TEN(10), JACK(11), QUEEN(12), KING(13)
}

/** A playing card. [id] is unique across the whole 2-deck shoe, since duplicate (suit, rank) pairs exist. */
sealed interface Card {
    val id: Int

    data class Standard(override val id: Int, val suit: Suit, val rank: Rank) : Card
    data class PrintedJoker(override val id: Int) : Card
}

/** True if this card is a wildcard: a printed joker, or a standard card of the game's wild rank. */
fun isWild(card: Card, wildRank: Rank): Boolean = when (card) {
    is Card.PrintedJoker -> true
    is Card.Standard -> card.rank == wildRank
}

enum class RummyTurn { PLAYER, BOT }
enum class RummyPhase { AWAITING_DRAW, AWAITING_DISCARD }

data class RummyState(
    val shoe: List<Card>,
    val discardPile: List<Card>,
    val playerHand: List<Card>,
    val botHand: List<Card>,
    val wildRank: Rank,
    val turn: RummyTurn,
    val phase: RummyPhase
)
