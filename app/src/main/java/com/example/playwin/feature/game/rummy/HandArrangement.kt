package com.example.playwin.feature.game.rummy

import com.example.playwin.core.domain.rummy.Card

/**
 * The player's own on-screen arrangement of their hand. Purely a display
 * order of card ids: it never adds, removes or changes the cards the
 * [com.example.playwin.core.domain.rummy.RummyEngine] says the player holds.
 */
internal object HandArrangement {

    /** The default arrangement: grouped by suit, ascending rank, printed jokers last. */
    fun sorted(hand: List<Card>): List<Card> =
        hand.sortedWith(compareBy({ it.sortSuit() }, { it.sortRank() }))

    /**
     * [hand] laid out in [order]. Ids no longer held are dropped; cards not in
     * [order] yet (a fresh draw) go on the end, so the player's arrangement
     * is never disturbed.
     */
    fun arrange(hand: List<Card>, order: List<Int>): List<Card> {
        val byId = hand.associateBy { it.id }
        val placed = order.mapNotNull { byId[it] }
        val placedIds = placed.mapTo(HashSet()) { it.id }
        return placed + hand.filterNot { it.id in placedIds }
    }

    /** [order] with the entry at [from] moved to [to]; out-of-range indices leave it unchanged. */
    fun <T> move(order: List<T>, from: Int, to: Int): List<T> {
        if (from !in order.indices || to !in order.indices || from == to) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun Card.sortSuit(): Int = when (this) {
        is Card.PrintedJoker -> 4
        is Card.Standard -> suit.ordinal
    }

    private fun Card.sortRank(): Int = when (this) {
        is Card.PrintedJoker -> 0
        is Card.Standard -> rank.order
    }
}

/**
 * Where the hand's cards sit: [perRow] equal slots per row, [stepX]/[stepY]
 * pixels apart (card size plus gap), wrapping left to right, top to bottom.
 */
internal data class HandGrid(val perRow: Int, val stepX: Float, val stepY: Float) {

    fun slotX(index: Int): Float = (index % perRow) * stepX
    fun slotY(index: Int): Float = (index / perRow) * stepY

    /** The slot (of [count]) whose top-left corner is nearest to a card whose top-left is at ([x], [y]). */
    fun nearestSlot(x: Float, y: Float, count: Int): Int {
        if (count <= 0) return 0
        val row = Math.round(y / stepY).coerceIn(0, (count - 1) / perRow)
        val col = Math.round(x / stepX).coerceIn(0, perRow - 1)
        return (row * perRow + col).coerceAtMost(count - 1)
    }
}
