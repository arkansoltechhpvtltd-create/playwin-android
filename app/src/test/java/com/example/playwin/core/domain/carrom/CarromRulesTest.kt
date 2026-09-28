package com.example.playwin.core.domain.carrom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `the queen is worth three`() {
        val match = CarromMatch(playerColors = mapOf(1 to CoinColor.WHITE, 2 to CoinColor.BLACK))
        val outcome = CarromRules.resolveShot(match, pocket(match, QUEEN_ID), potted = listOf(QUEEN_ID))
        assertEquals(3, outcome.pointsGained)
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
