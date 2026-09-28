package com.example.playwin.core.domain.rummy

/**
 * Validates a 13-card Indian Rummy declaration: the hand must partition
 * completely into groups - sequences (3+ consecutive same-suit cards) and
 * sets (3-4 same-rank, different-suit cards) - with jokers/wild-rank cards
 * substituting for any card, and at least one of the sequences must be
 * PURE (contain no wildcard at all).
 *
 * This is a pure-Kotlin backtracking search: repeatedly peel a valid group
 * containing the first remaining card off the hand, recursing on what's
 * left, until either every card is consumed (success, if a pure sequence
 * was used somewhere) or every option is exhausted (failure).
 */
object RummyValidator {

    fun isValidDeclaration(cards: List<Card>, wildRank: Rank): Boolean {
        if (cards.size != 13) return false
        return search(cards, wildRank, hasPureSequence = false)
    }

    private fun search(cards: List<Card>, wildRank: Rank, hasPureSequence: Boolean): Boolean {
        if (cards.isEmpty()) return hasPureSequence

        val first = cards.first()
        val rest = cards.drop(1)

        for (groupSize in 3..cards.size) {
            val pickCount = groupSize - 1
            if (pickCount > rest.size) continue

            for (combo in combinations(rest, pickCount)) {
                val group = listOf(first) + combo
                val isPure = evaluateGroup(group, wildRank) ?: continue
                val remaining = subtractById(rest, combo)
                if (search(remaining, wildRank, hasPureSequence || isPure)) return true
            }
        }
        return false
    }

    /** Null if [group] is neither a valid set nor a valid sequence; otherwise true iff it's a pure sequence. */
    internal fun evaluateGroup(group: List<Card>, wildRank: Rank): Boolean? {
        if (isValidSet(group, wildRank)) return false
        if (isValidSequence(group, wildRank)) return group.none { isWild(it, wildRank) }
        return null
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

    private fun isValidSequence(cards: List<Card>, wildRank: Rank): Boolean {
        if (cards.size < 3) return false
        val wilds = cards.count { isWild(it, wildRank) }
        val real = cards.filterIsInstance<Card.Standard>().filter { it.rank != wildRank }
        if (real.isEmpty()) return true

        val suit = real.first().suit
        if (real.any { it.suit != suit }) return false

        val ranks = real.map { it.rank.order }
        if (ranks.toSet().size != ranks.size) return false // no duplicate ranks of the same suit in one run

        val minRank = ranks.min()
        val maxRank = ranks.max()
        val span = maxRank - minRank + 1
        val gapsNeeded = span - real.size
        if (gapsNeeded > wilds) return false

        // The final run has length cards.size; check a window of that length containing
        // [minRank, maxRank] fits within the 1..13 rank range (Ace is low only).
        val lowBoundMin = maxOf(1, maxRank - cards.size + 1)
        val lowBoundMax = minOf(minRank, 13 - cards.size + 1)
        return lowBoundMin <= lowBoundMax
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
