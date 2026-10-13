package com.example.playwin.core.domain.rummy

/** What kind of meld a group of cards forms. */
enum class GroupKind {
    /** Same-suit consecutive run with no card acting as a joker. */
    PURE_SEQUENCE,
    /** Same-suit consecutive run that needs a joker to fill a gap or stand in for a card. */
    IMPURE_SEQUENCE,
    /** 3-4 cards of one rank, all different suits (jokers may fill in). */
    SET;

    val isSequence: Boolean get() = this != SET
}

/**
 * Validates a 13-card Indian Rummy declaration: the hand must partition
 * completely into groups - sequences (3+ consecutive same-suit cards) and
 * sets (3-4 same-rank, different-suit cards) - with at least TWO sequences,
 * at least one of them PURE. The rest may be further sequences or sets.
 *
 * Jokers: a printed joker, or a card of the game's wild rank, may stand in
 * for any card. A run is pure when no card in it is *acting as* a joker, so
 * a wild-rank card used as its real self in its natural run (wild rank 5,
 * 4♥-5♥-6♥) keeps the run pure; a printed joker never does.
 *
 * Aces: in a run an Ace is either low (A-2-3) or high (Q-K-A), never both,
 * so a run cannot wrap around (K-A-2 is not a sequence).
 *
 * This is a pure-Kotlin backtracking search: repeatedly peel a valid group
 * containing the first remaining card off the hand, recursing on what's
 * left, until either every card is consumed (success, if enough sequences
 * were used) or every option is exhausted (failure).
 */
object RummyValidator {

    /** How many sequences a declaration needs, at least one of them pure. */
    const val REQUIRED_SEQUENCES = 2

    fun isValidDeclaration(cards: List<Card>, wildRank: Rank): Boolean {
        if (cards.size != 13) return false
        return search(cards, wildRank, hasPureSequence = false, sequences = 0)
    }

    private fun search(cards: List<Card>, wildRank: Rank, hasPureSequence: Boolean, sequences: Int): Boolean {
        if (cards.isEmpty()) return hasPureSequence && sequences >= REQUIRED_SEQUENCES

        val first = cards.first()
        val rest = cards.drop(1)

        for (groupSize in 3..cards.size) {
            val pickCount = groupSize - 1
            if (pickCount > rest.size) continue

            for (combo in combinations(rest, pickCount)) {
                val group = listOf(first) + combo
                val kind = classifyGroup(group, wildRank) ?: continue
                val remaining = subtractById(rest, combo)
                val found = search(
                    remaining,
                    wildRank,
                    hasPureSequence || kind == GroupKind.PURE_SEQUENCE,
                    if (kind.isSequence) sequences + 1 else sequences
                )
                if (found) return true
            }
        }
        return false
    }

    /**
     * What [group] can be played as, or null if it's no valid meld. A group
     * that qualifies as more than one kind gets the most useful one: a pure
     * sequence over an impure one, any sequence over a set.
     */
    fun classifyGroup(group: List<Card>, wildRank: Rank): GroupKind? = when {
        isPureSequence(group) -> GroupKind.PURE_SEQUENCE
        isSequenceWithJokers(group, wildRank) -> GroupKind.IMPURE_SEQUENCE
        isValidSet(group, wildRank) -> GroupKind.SET
        else -> null
    }

    private fun isValidSet(cards: List<Card>, wildRank: Rank): Boolean {
        if (cards.size !in 3..4) return false
        val real = cards.filterIsInstance<Card.Standard>().filter { it.rank != wildRank }
        if (real.isEmpty()) return true

        val rank = real.first().rank
        if (real.any { it.rank != rank }) return false
        val suits = real.map { it.suit }
        return suits.toSet().size == suits.size
    }

    /** Every card is its real self - no printed jokers, wild-rank cards in their natural place - in one unbroken run. */
    private fun isPureSequence(cards: List<Card>): Boolean {
        if (cards.size < 3) return false
        val real = cards.filterIsInstance<Card.Standard>()
        if (real.size != cards.size) return false
        if (real.any { it.suit != real.first().suit }) return false
        return AceRanking.entries.any { ace ->
            val ranks = real.map { ace.valueOf(it.rank) }
            ranks.toSet().size == ranks.size && ranks.max() - ranks.min() + 1 == ranks.size
        }
    }

    /** A run where every printed joker and wild-rank card may stand in for whatever card is missing. */
    private fun isSequenceWithJokers(cards: List<Card>, wildRank: Rank): Boolean {
        if (cards.size < 3) return false
        val wilds = cards.count { isWild(it, wildRank) }
        val real = cards.filterIsInstance<Card.Standard>().filter { it.rank != wildRank }
        if (real.isEmpty()) return true

        val suit = real.first().suit
        if (real.any { it.suit != suit }) return false

        return AceRanking.entries.any { ace ->
            val ranks = real.map { ace.valueOf(it.rank) }
            if (ranks.toSet().size != ranks.size) return@any false // no duplicate ranks of the same suit in one run

            val minRank = ranks.min()
            val maxRank = ranks.max()
            val gapsNeeded = (maxRank - minRank + 1) - real.size
            if (gapsNeeded > wilds) return@any false

            // The final run has length cards.size; check a window of that length containing
            // [minRank, maxRank] fits within this ace ranking's range.
            val lowBoundMin = maxOf(ace.lowest, maxRank - cards.size + 1)
            val lowBoundMax = minOf(minRank, ace.highest - cards.size + 1)
            lowBoundMin <= lowBoundMax
        }
    }

    /**
     * The two ways to read an Ace in a run. Each run is checked under one
     * reading at a time, so an Ace can't be low and high at once (no K-A-2).
     */
    private enum class AceRanking(val lowest: Int, val highest: Int) {
        LOW(1, 13),
        HIGH(2, 14);

        fun valueOf(rank: Rank): Int = if (rank == Rank.ACE && this == HIGH) 14 else rank.order
    }

    private fun <T> combinations(items: List<T>, k: Int): List<List<T>> {
        if (k == 0) return listOf(emptyList())
        if (items.size < k) return emptyList()
        val head = items.first()
        val tail = items.drop(1)
        return combinations(tail, k - 1).map { listOf(head) + it } + combinations(tail, k)
    }

    private fun subtractById(all: List<Card>, toRemove: List<Card>): List<Card> {
        val ids = toRemove.map { it.id }.toSet()
        return all.filterNot { it.id in ids }
    }
}
