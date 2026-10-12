package com.example.playwin.core.domain.carrom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarromPocketTest {

    private fun lone(position: Vec2, velocity: Vec2 = Vec2.Zero) = listOf(Coin(id = 1, position = position, velocity = velocity))

    @Test
    fun `a coin sent from the centre at each painted hole sinks`() {
        CarromBoard.corners.forEach { hole ->
            val start = Vec2(CarromBoard.SIZE / 2f, CarromBoard.SIZE / 2f)
            val result = CarromPhysics.simulate(lone(start, (hole - start).normalizedOrZero() * 900f))
            assertEquals("hole at $hole", listOf(1), result.newlyPocketed)
        }
    }

    @Test
    fun `a coin sinks just inside a hole's rim but not just outside it`() {
        CarromBoard.corners.forEach { hole ->
            val outward = (hole - Vec2(CarromBoard.SIZE / 2f, CarromBoard.SIZE / 2f)).normalizedOrZero()
            // Approach from the board side, where there's room for a coin outside the rim.
            val inside = hole - outward * (CarromBoard.POCKET_RADIUS - 1f)
            val outside = hole - outward * (CarromBoard.POCKET_RADIUS + 1f)
            assertTrue(CarromPhysics.step(lone(inside), 1f / 60f).coins.single().pocketed)
            assertFalse(CarromPhysics.step(lone(outside), 1f / 60f).coins.single().pocketed)
        }
    }

    @Test
    fun `a coin rolling past a corner but clear of its hole stays on the board`() {
        // Along the top, just clear of the top-right hole's rim, out to the right wall and back.
        val y = CarromBoard.corners[1].y + CarromBoard.POCKET_RADIUS + 1f
        var coins = lone(Vec2(450f, y), Vec2(450f, 0f))
        var furthest = 0f
        repeat(600) {
            val result = CarromPhysics.step(coins, 1f / 60f)
            assertTrue(result.newlyPocketed.isEmpty())
            coins = result.coins
            furthest = maxOf(furthest, coins.single().position.x)
        }
        assertEquals(CarromBoard.SIZE - CarromBoard.COIN_RADIUS, furthest, 1f)
    }
}
