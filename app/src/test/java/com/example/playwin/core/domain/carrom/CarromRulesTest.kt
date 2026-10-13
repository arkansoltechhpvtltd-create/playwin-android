package com.example.playwin.core.domain.carrom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarromRulesTest {

    private fun pocket(match: CarromMatch, vararg ids: Int): List<Coin> =
        match.coins.map { if (it.id in ids) it.copy(pocketed = true) else it }

    @Test
    fun `the first coin potted decides colors, scores, and keeps the turn`() {
        val match = CarromMatch()
        val outcome = CarromRules.resolveShot(match, pocket(match, 10), potted = listOf(10))
        assertEquals(mapOf(1 to CoinColor.BLACK, 2 to CoinColor.WHITE), outcome.after.playerColors)
        assertEquals(1, outcome.after.scores[1])
        assertEquals(1, outcome.after.currentPlayer)
    }

    // Queen cover rule. Player 1 (the human in vs-Computer) owns white, player 2 (the computer) black.
    private val colors = mapOf(1 to CoinColor.WHITE, 2 to CoinColor.BLACK)
    private val ownCoin = mapOf(1 to 1, 2 to 10)

    /** [player] pots the queen alone; returns the match with the cover now owed. */
    private fun queenPotted(player: Int): CarromMatch {
        val match = CarromMatch(currentPlayer = player, playerColors = colors)
        val outcome = CarromRules.resolveShot(match, pocket(match, QUEEN_ID), potted = listOf(QUEEN_ID))
        assertEquals(QueenEvent.COVER_OWED, outcome.queenEvent)
        assertEquals(0, outcome.pointsGained)
        assertEquals(player, outcome.after.queenCoverOwedBy)
        assertEquals("potting the queen earns the cover shot", player, outcome.after.currentPlayer)
        return outcome.after
    }

    private fun queenPottedThenOwnCoinNextShotIsClaimed(player: Int) {
        val owed = queenPotted(player)
        val coin = ownCoin.getValue(player)
        val outcome = CarromRules.resolveShot(owed, pocket(owed, coin), potted = listOf(coin))
        assertEquals(QueenEvent.CLAIMED, outcome.queenEvent)
        assertEquals(1 + QUEEN_POINTS, outcome.pointsGained)
        assertEquals(1 + QUEEN_POINTS, outcome.after.scores[player])
        assertNull(outcome.after.queenCoverOwedBy)
        assertTrue(outcome.after.coins.first { it.id == QUEEN_ID }.pocketed)
        assertEquals(player, outcome.after.currentPlayer)
    }

    private fun queenPottedThenNoOwnCoinNextShotIsReturned(player: Int) {
        val owed = queenPotted(player)
        val outcome = CarromRules.resolveShot(owed, owed.coins, potted = emptyList())
        assertEquals(QueenEvent.RETURNED, outcome.queenEvent)
        assertEquals(0, outcome.pointsGained)
        assertEquals(0, outcome.after.scores[player])
        assertNull(outcome.after.queenCoverOwedBy)
        assertEquals(3 - player, outcome.after.currentPlayer)
        val queen = outcome.after.coins.first { it.id == QUEEN_ID }
        assertFalse(queen.pocketed)
        assertEquals(Vec2(CarromBoard.SIZE / 2f, CarromBoard.SIZE / 2f), queen.position)
        assertFalse(outcome.after.boardCleared)
    }

    private fun queenAndOwnCoinSameShotIsClaimed(player: Int) {
        val match = CarromMatch(currentPlayer = player, playerColors = colors)
        val coin = ownCoin.getValue(player)
        val outcome = CarromRules.resolveShot(match, pocket(match, QUEEN_ID, coin), potted = listOf(QUEEN_ID, coin))
        assertEquals(QueenEvent.CLAIMED, outcome.queenEvent)
        assertEquals(1 + QUEEN_POINTS, outcome.after.scores[player])
        assertNull(outcome.after.queenCoverOwedBy)
    }

    @Test fun `player - queen then own coin next shot claims the queen`() = queenPottedThenOwnCoinNextShotIsClaimed(1)
    @Test fun `computer - queen then own coin next shot claims the queen`() = queenPottedThenOwnCoinNextShotIsClaimed(2)
    @Test fun `player - queen then no own coin next shot returns it`() = queenPottedThenNoOwnCoinNextShotIsReturned(1)
    @Test fun `computer - queen then no own coin next shot returns it`() = queenPottedThenNoOwnCoinNextShotIsReturned(2)
    @Test fun `player - queen and own coin on the same shot claims the queen`() = queenAndOwnCoinSameShotIsClaimed(1)
    @Test fun `computer - queen and own coin on the same shot claims the queen`() = queenAndOwnCoinSameShotIsClaimed(2)

    @Test
    fun `potting only the opponent's coin on the cover shot returns the queen`() {
        val owed = queenPotted(1)
        val outcome = CarromRules.resolveShot(owed, pocket(owed, 10), potted = listOf(10))
        assertEquals(QueenEvent.RETURNED, outcome.queenEvent)
        assertEquals(0, outcome.after.scores[1])
    }

    @Test
    fun `fouling on the cover shot returns the queen`() {
        val owed = queenPotted(1)
        val outcome = CarromRules.resolveShot(owed, pocket(owed, STRIKER_ID, 1), potted = listOf(STRIKER_ID, 1))
        assertEquals(QueenEvent.RETURNED, outcome.queenEvent)
        assertEquals(0, outcome.after.scores[1])
    }

    @Test
    fun `timing out with a cover owed returns the queen`() {
        val passed = CarromRules.passTurn(queenPotted(1))
        assertNull(passed.queenCoverOwedBy)
        assertFalse(passed.coins.first { it.id == QUEEN_ID }.pocketed)
        assertEquals(2, passed.currentPlayer)
    }

    @Test
    fun `the returned queen goes to the centre when it is free`() {
        val owed = queenPotted(1)
        // Clear the middle so the centre spot is open.
        val cleared = owed.coins.map { if (it.id != STRIKER_ID && it.id != QUEEN_ID && it.id != 1) it.copy(pocketed = true) else it }
            .map { if (it.id == 1) it.copy(position = Vec2(100f, 100f)) else it }
        val outcome = CarromRules.resolveShot(owed.copy(coins = cleared), cleared, potted = emptyList())
        assertEquals(Vec2(CarromBoard.SIZE / 2f, CarromBoard.SIZE / 2f), outcome.after.coins.first { it.id == QUEEN_ID }.position)
    }

    @Test
    fun `the returned queen takes the nearest free spot when the centre is taken`() {
        val center = Vec2(CarromBoard.SIZE / 2f, CarromBoard.SIZE / 2f)
        val coins = listOf(
            Coin(STRIKER_ID, CarromBoard.baselineFor(1)),
            Coin(QUEEN_ID, center, pocketed = true),
            Coin(1, center)
        )
        val queen = CarromRules.respotQueen(coins).first { it.id == QUEEN_ID }
        assertFalse(queen.pocketed)
        val gap = (queen.position - center).length
        assertTrue("queen overlaps the coin at the centre", gap >= 2 * CarromBoard.COIN_RADIUS)
        assertTrue("queen was put further out than needed: $gap", gap <= 2 * CarromBoard.COIN_RADIUS + CarromBoard.COIN_RADIUS / 2f)
    }

    @Test
    fun `the queen is not an opponent coin for either side`() {
        val match = CarromMatch(currentPlayer = 2, playerColors = colors)
        val outcome = CarromRules.resolveShot(match, pocket(match, QUEEN_ID), potted = listOf(QUEEN_ID))
        assertFalse(outcome.fouled)
        assertEquals(0, outcome.after.scores[1])
        assertEquals(0, outcome.after.scores[2])
    }

    @Test
    fun `potting the striker is a foul - no points, turn passes, striker re-spotted`() {
        val match = CarromMatch(playerColors = mapOf(1 to CoinColor.WHITE, 2 to CoinColor.BLACK))
        val outcome = CarromRules.resolveShot(match, pocket(match, STRIKER_ID, 1), potted = listOf(STRIKER_ID, 1))
        assertTrue(outcome.fouled)
        assertEquals(0, outcome.pointsGained)
        assertEquals(2, outcome.after.currentPlayer)
        val striker = outcome.after.coins.first { it.id == STRIKER_ID }
        assertFalse(striker.pocketed)
        assertEquals(CarromBoard.baselineFor(2), striker.position)
    }

    @Test
    fun `potting only the opponent's coin scores nothing and passes the turn`() {
        val match = CarromMatch(playerColors = mapOf(1 to CoinColor.WHITE, 2 to CoinColor.BLACK))
        val outcome = CarromRules.resolveShot(match, pocket(match, 10), potted = listOf(10))
        assertEquals(0, outcome.pointsGained)
        assertEquals(2, outcome.after.currentPlayer)
    }

    @Test
    fun `passing the turn moves the striker to the other baseline`() {
        val passed = CarromRules.passTurn(CarromMatch())
        assertEquals(2, passed.currentPlayer)
        assertEquals(CarromBoard.baselineFor(2), passed.coins.first { it.id == STRIKER_ID }.position)
    }

    @Test
    fun `a simulated shot comes to rest`() {
        val match = CarromMatch()
        val result = CarromPhysics.simulate(CarromRules.launch(match.coins, Vec2(0f, -1f), 1f))
        assertFalse(result.anyMoving)
        assertTrue(result.coins.all { it.velocity == Vec2.Zero })
    }
}
