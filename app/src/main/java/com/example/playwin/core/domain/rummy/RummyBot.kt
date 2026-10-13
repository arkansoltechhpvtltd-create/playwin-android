package com.example.playwin.core.domain.rummy

/**
 * The "Play vs Computer" rummy opponent. Every decision is driven by
 * [deadwood]: the lowest total points left in cards that can't be placed in
 * a valid set or sequence (using exactly [RummyValidator]'s group rules),
 * with a heavy penalty if the hand has no pure sequence yet and a lighter one
 * if it lacks a second sequence - a declaration needs both.
 *
 * On its turn it:
 *  1. takes the top discard only if that genuinely lowers its best deadwood
 *     (or it's a wildcard), otherwise draws blind from the shoe;
 *  2. declares if some discard leaves a valid 13-card hand
 *     ([RummyValidator.isValidDeclaration]);
 *  3. otherwise discards the card whose removal leaves the least deadwood,
 *     never throwing away a wildcard.
 */
object RummyBot {

    /** Deadwood charged for a hand with no pure sequence: in real rummy nothing counts as melded without one. */
    private const val NO_PURE_SEQUENCE_PENALTY = 40

    /** Deadwood charged for a hand that has its pure sequence but can't yet form the second sequence a declaration needs. */
    private const val NO_SECOND_SEQUENCE_PENALTY = 20
    private const val MAX_GROUP_SIZE = 5 // a longer run can always be split into two groups of 3+

    sealed interface Discard {
        val card: Card

        /** Throw [card] away and play on. */
        data class Play(override val card: Card) : Discard

        /** Set [card] aside and declare the remaining 13 cards. */
        data class Declare(override val card: Card) : Discard
    }

    /** The outcome of one computer turn. */
    sealed interface Turn {
        val state: RummyState

        /** Drew and discarded; it's the player's turn now. */
        data class Played(override val state: RummyState) : Turn

        /** Drew, set a card aside, and declares [declaredHand] - still to be confirmed by the game engine. */
        data class Declared(override val state: RummyState, val declaredHand: List<Card>) : Turn
    }

    /** Plays the computer's whole turn (draw, then discard or declare) from a state where it's [RummyTurn.BOT]'s move. */
    fun playTurn(state: RummyState): Turn {
        require(state.turn == RummyTurn.BOT && state.phase == RummyPhase.AWAITING_DRAW)
        val afterDraw = if (shouldTakeDiscard(state.botHand, state.discardPile.lastOrNull(), state.wildRank)) {
            RummyEngine.drawFromDiscard(state)
        } else {
            RummyEngine.drawFromShoe(state)
        }
        return when (val choice = chooseDiscard(afterDraw.botHand, afterDraw.wildRank)) {
            is Discard.Declare -> Turn.Declared(afterDraw, afterDraw.botHand.filterNot { it.id == choice.card.id })
            is Discard.Play -> Turn.Played(RummyEngine.discard(afterDraw, choice.card))
        }
    }

    /** Whether to take the top of the discard pile rather than draw blind. */
    fun shouldTakeDiscard(hand: List<Card>, topDiscard: Card?, wildRank: Rank): Boolean {
        if (topDiscard == null) return false
        if (isWild(topDiscard, wildRank)) return true
        val withDiscard = bestDiscard(hand + topDiscard, wildRank)
        // It must pay for itself: the card we'd throw back can't be the one we just took.
        return withDiscard.first.id != topDiscard.id && withDiscard.second < deadwood(hand, wildRank)
    }

    /** With a 14-card [hand] after drawing: declare if possible, otherwise the best card to throw. */
    fun chooseDiscard(hand: List<Card>, wildRank: Rank): Discard {
        val (card, remaining) = bestDiscard(hand, wildRank)
        // Zero deadwood (two sequences, one pure) is exactly a valid declaration; confirm with the validator anyway.
        val declarable = remaining == 0 &&
            RummyValidator.isValidDeclaration(hand.filterNot { it.id == card.id }, wildRank)
        return if (declarable) Discard.Declare(card) else Discard.Play(card)
    }

    /** The card whose removal leaves the least deadwood (ties: throw the higher-point card), and that deadwood. */
    private fun bestDiscard(hand: List<Card>, wildRank: Rank): Pair<Card, Int> =
        hand.filterNot { isWild(it, wildRank) }
            .ifEmpty { hand }
            .map { card -> card to deadwood(hand.filterNot { it.id == card.id }, wildRank) }
            .minWith(compareBy<Pair<Card, Int>> { it.second }.thenByDescending { points(it.first, wildRank) })

    /** Rummy points: face cards and aces 10, number cards their face value, wildcards 0. */
    fun points(card: Card, wildRank: Rank): Int = when {
        isWild(card, wildRank) -> 0
        card is Card.Standard && (card.rank == Rank.ACE || card.rank.order >= Rank.TEN.order) -> 10
        card is Card.Standard -> card.rank.order
        else -> 0
    }

    /**
     * The lowest deadwood achievable by arranging [hand] into valid groups.
     * A bitmask DP over which cards are still unplaced: the lowest remaining
     * card is either left as deadwood, or placed in some valid group of up
     * to [MAX_GROUP_SIZE] cards drawn from what's left. The DP also tracks
     * what the arrangement still owes a declaration (a pure sequence, and
     * [RummyValidator.REQUIRED_SEQUENCES] sequences in all), charging
     * [NO_PURE_SEQUENCE_PENALTY] or [NO_SECOND_SEQUENCE_PENALTY] when the
     * hand can't meet that yet.
     */
    fun deadwood(hand: List<Card>, wildRank: Rank): Int {
        val n = hand.size
        require(n <= 20) { "Hand too large to evaluate" }

        // Every valid group, as a bitmask, filed under its lowest card index.
        val groupsByLowest = List(n) { mutableListOf<Pair<Int, GroupKind>>() }
        fun collect(start: Int, chosen: List<Int>) {
            if (chosen.size >= 3) {
                val kind = RummyValidator.classifyGroup(chosen.map { hand[it] }, wildRank)
                if (kind != null) groupsByLowest[chosen.first()] += chosen.fold(0) { m, i -> m or (1 shl i) } to kind
            }
            if (chosen.size == MAX_GROUP_SIZE) return
            for (i in start until n) collect(i + 1, chosen + i)
        }
        for (i in 0 until n) collect(i + 1, listOf(i))

        // best(mask, needPure, needSequences) = lowest deadwood of the cards in mask, given the
        // groups placed among them must still supply a pure sequence (if needPure) and
        // needSequences more sequences.
        val memo = HashMap<Long, Int>()
        val impossible = Int.MAX_VALUE / 4
        fun best(mask: Int, needPure: Boolean, needSequences: Int): Int {
            if (mask == 0) return if (needPure || needSequences > 0) impossible else 0
            val key = (mask.toLong() shl 3) or ((if (needPure) 4L else 0L) or needSequences.toLong())
            memo[key]?.let { return it }
            val lowest = Integer.numberOfTrailingZeros(mask)
            var result = minOf(impossible, points(hand[lowest], wildRank) + best(mask and (1 shl lowest).inv(), needPure, needSequences))
            for ((group, kind) in groupsByLowest[lowest]) {
                if (group and mask != group) continue
                val after = best(
                    mask and group.inv(),
                    needPure && kind != GroupKind.PURE_SEQUENCE,
                    if (kind.isSequence) maxOf(0, needSequences - 1) else needSequences
                )
                result = minOf(result, after)
            }
            return result.also { memo[key] = it }
        }

        val full = (1 shl n) - 1
        val declarable = best(full, needPure = true, needSequences = RummyValidator.REQUIRED_SEQUENCES)
        if (declarable < impossible) return declarable
        val pureOnly = best(full, needPure = true, needSequences = 1)
        if (pureOnly < impossible) return pureOnly + NO_SECOND_SEQUENCE_PENALTY
        return best(full, needPure = false, needSequences = 0) + NO_PURE_SEQUENCE_PENALTY
    }
}
