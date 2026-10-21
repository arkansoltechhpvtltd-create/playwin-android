package com.example.playwin.core.domain.carrom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The striker comes back to its baseline's centre, or the nearest free spot along it - never on top of a coin. */
class CarromStrikerRespotTest {

    private val clearance = CarromBoard.STRIKER_RADIUS + CarromBoard.COIN_RADIUS

    private fun striker(coins: List<Coin>) = coins.first { it.id == STRIKER_ID }

    private fun assertClearOfCoins(coins: List<Coin>) {
        val s = striker(coins)
        coins.filter { it.id != STRIKER_ID && !it.pocketed }.forEach {
            assertTrue("striker overlaps coin ${it.id}", (it.position - s.position).length >= clearance - 0.01f)
        }
    }

    @Test
    fun `an empty baseline puts the striker at its centre`() {
        listOf(1, 2).forEach { player ->
            val coins = CarromRules.respotStriker(listOf(Coin(STRIKER_ID, Vec2(100f, 100f), Vec2(50f, 0f))), player)
            assertEquals(CarromBoard.baselineFor(player), striker(coins).position)
            assertEquals(Vec2.Zero, striker(coins).velocity)
        }
    }

    @Test
    fun `a coin on the baseline's centre moves the striker to the nearest clear spot beside it`() {
        listOf(1, 2).forEach { player ->
            val home = CarromBoard.baselineFor(player)
            val coins = CarromRules.respotStriker(listOf(Coin(STRIKER_ID, Vec2(100f, 100f)), Coin(1, home)), player)
            val s = striker(coins)
            assertEquals("stays on the baseline", home.y, s.position.y, 0.001f)
            assertEquals("just clear of the coin", clearance, abs(s.position.x - home.x), 1f)
            assertClearOfCoins(coins)
        }
    }

    @Test
    fun `a coin just off the centre pushes the striker to the other side`() {
        val home = CarromBoard.baselineFor(1)
        val coins = CarromRules.respotStriker(listOf(Coin(STRIKER_ID, home), Coin(1, Vec2(home.x - 10f, home.y))), 1)
        assertTrue("moved away from the coin", striker(coins).position.x > home.x)
        assertClearOfCoins(coins)
    }

    @Test
    fun `the striker never goes further out than it could be slid`() {
        val home = CarromBoard.baselineFor(1)
        // Coins all along the baseline except one gap near the left end.
        val gapX = home.x - CarromBoard.STRIKER_SLIDE_HALF_SPAN + 2f
        val blockers = generateSequence(home.x - CarromBoard.STRIKER_SLIDE_HALF_SPAN - 60f) { it + 2 * CarromBoard.COIN_RADIUS }
            .takeWhile { it < home.x + CarromBoard.STRIKER_SLIDE_HALF_SPAN + 60f }
            .filter { abs(it - gapX) >= clearance }
            .mapIndexed { i, x -> Coin(i + 1, Vec2(x, home.y)) }
            .toList()
        val s = striker(CarromRules.respotStriker(listOf(Coin(STRIKER_ID, home)) + blockers, 1))
        assertTrue("slid out to ${s.position.x}", abs(s.position.x - home.x) <= CarromBoard.STRIKER_SLIDE_HALF_SPAN + 0.001f)
    }

    @Test
    fun `pocketed coins don't block the centre`() {
        val home = CarromBoard.baselineFor(1)
        val coins = CarromRules.respotStriker(listOf(Coin(STRIKER_ID, Vec2(100f, 100f)), Coin(1, home, pocketed = true)), 1)
        assertEquals(home, striker(coins).position)
    }
}
