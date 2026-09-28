package com.example.playwin.core.domain.rummy

/**
 * Game mechanics for 13-card Indian Rummy against the computer: building the
 * 2-deck shoe, dealing, and drawing/discarding ([RummyBot] decides the
 * computer's moves). Whether a declared hand actually wins is
 * [RummyValidator]'s job, never decided here.
 */
object RummyEngine {

    const val HAND_SIZE = 13

    /** 2 standard 52-card decks plus one printed joker per deck (106 cards total). */
    fun buildShoe(): List<Card> {
        var nextId = 0
        val cards = mutableListOf<Card>()
        repeat(2) {
            for (suit in Suit.entries) {
                for (rank in Rank.entries) {
                    cards += Card.Standard(nextId++, suit, rank)
                }
            }
            cards += Card.PrintedJoker(nextId++)
        }
        return cards
    }

    fun deal(): RummyState {
        val shoe = buildShoe().shuffled()
        val playerHand = shoe.subList(0, HAND_SIZE)
        val botHand = shoe.subList(HAND_SIZE, HAND_SIZE * 2)

        // The next card off the shoe sets the wild rank (a printed joker cut card defaults to Ace).
        val wildCutCard = shoe[HAND_SIZE * 2]
        val wildRank = (wildCutCard as? Card.Standard)?.rank ?: Rank.ACE

        val firstDiscard = shoe[HAND_SIZE * 2 + 1]
        val remainingShoe = shoe.drop(HAND_SIZE * 2 + 2)

        return RummyState(
            shoe = remainingShoe,
            discardPile = listOf(firstDiscard),
            playerHand = playerHand,
            botHand = botHand,
            wildRank = wildRank,
            turn = RummyTurn.PLAYER,
            phase = RummyPhase.AWAITING_DRAW
        )
    }

    fun drawFromShoe(state: RummyState): RummyState {
        require(state.phase == RummyPhase.AWAITING_DRAW)
        val (shoe, drawn) = reshuffleIfNeeded(state.shoe, state.discardPile)
        val card = shoe.first()
        val newShoe = shoe.drop(1)
        return withDrawnCard(state, card).copy(shoe = newShoe, discardPile = drawn, phase = RummyPhase.AWAITING_DISCARD)
    }

    fun drawFromDiscard(state: RummyState): RummyState {
        require(state.phase == RummyPhase.AWAITING_DRAW)
        require(state.discardPile.isNotEmpty())
        val card = state.discardPile.last()
        val newDiscard = state.discardPile.dropLast(1)
        return withDrawnCard(state, card).copy(discardPile = newDiscard, phase = RummyPhase.AWAITING_DISCARD)
    }

    fun discard(state: RummyState, card: Card): RummyState {
        require(state.phase == RummyPhase.AWAITING_DISCARD)
        val hand = handFor(state, state.turn)
        val newHand = hand.filterNot { it.id == card.id }
        val nextTurn = if (state.turn == RummyTurn.PLAYER) RummyTurn.BOT else RummyTurn.PLAYER
        return withHand(state, state.turn, newHand).copy(
            discardPile = state.discardPile + card,
            turn = nextTurn,
            phase = RummyPhase.AWAITING_DRAW
        )
    }

    private fun handFor(state: RummyState, turn: RummyTurn): List<Card> =
        if (turn == RummyTurn.PLAYER) state.playerHand else state.botHand

    private fun withHand(state: RummyState, turn: RummyTurn, hand: List<Card>): RummyState =
        if (turn == RummyTurn.PLAYER) state.copy(playerHand = hand) else state.copy(botHand = hand)

    private fun withDrawnCard(state: RummyState, card: Card): RummyState =
        withHand(state, state.turn, handFor(state, state.turn) + card)

    /** If the shoe runs dry, reshuffle everything but the top discard back into a fresh shoe. */
    private fun reshuffleIfNeeded(shoe: List<Card>, discardPile: List<Card>): Pair<List<Card>, List<Card>> {
        if (shoe.isNotEmpty()) return shoe to discardPile
        val top = discardPile.lastOrNull() ?: return shoe to discardPile
        val reshuffled = discardPile.dropLast(1).shuffled()
        return reshuffled to listOf(top)
    }
}
