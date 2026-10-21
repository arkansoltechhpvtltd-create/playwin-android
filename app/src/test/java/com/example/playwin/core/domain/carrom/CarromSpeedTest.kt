package com.example.playwin.core.domain.carrom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * At the fastest a piece ever moves - a full-power strike, and well past it -
 * nothing passes through a wall, a pocket or another piece, nothing is left
 * overlapping, and every shot still comes to rest.
 */
class CarromSpeedTest {

    private val topSpeed = CarromPhysics.MAX_SHOT_SPEED
    private val centre = Vec2(CarromBoard.SIZE / 2f, CarromBoard.SIZE / 2f)

    /** The longest frame [CarromPhysics.step] takes - a slow phone's worst case. */
    private val longFrame = 1f / 30f

    private class Run(val coins: List<Coin>, val pocketed: List<Int>, val seconds: Float)

    /** Plays [coins] out in [longFrame] frames, checking every frame that each piece is on the board. */
    private fun runToRest(coins: List<Coin>, maxSeconds: Float = 10f): Run {
        var current = coins
        val pocketed = mutableListOf<Int>()
        var seconds = 0f
        while (seconds < maxSeconds) {
            val result = CarromPhysics.step(current, longFrame)
            current = result.coins
            pocketed += result.newlyPocketed
            seconds += longFrame
            current.filter { !it.pocketed }.forEach { coin ->
                val r = CarromBoard.radiusOf(coin.id)
                val range = r - 0.01f..CarromBoard.SIZE - r + 0.01f
                assertTrue("piece ${coin.id} through a wall at ${coin.position}", coin.position.x in range && coin.position.y in range)
            }
            if (!result.anyMoving) return Run(current, pocketed, seconds)
        }
        throw AssertionError("still moving after $maxSeconds s")
    }

    private fun assertNoOverlaps(coins: List<Coin>) {
        val on = coins.filter { !it.pocketed }
        for (i in on.indices) for (j in i + 1 until on.size) {
            val minDist = CarromBoard.radiusOf(on[i].id) + CarromBoard.radiusOf(on[j].id)
            val dist = (on[i].position - on[j].position).length
            assertTrue("pieces ${on[i].id} and ${on[j].id} overlap by ${minDist - dist}", dist >= minDist - 0.01f)
        }
    }

    @Test
    fun `no substep moves a piece further than a quarter of a coin's radius`() {
        assertTrue(CarromPhysics.MAX_TRAVEL_PER_SUBSTEP <= CarromBoard.COIN_RADIUS / 4f)
    }

    @Test
    fun `the starting flower has no overlaps`() {
        assertNoOverlaps(CarromBoard.initialSetup())
    }

    @Test
    fun `two coins meeting head on at twice top speed bounce off rather than pass through`() {
        // Every starting gap, so no lucky spacing lets them land overlapping on some check.
        for (gap in 0 until 60 step 2) {
            val a = Coin(id = 1, position = Vec2(250f, centre.y), velocity = Vec2(topSpeed * 2f, 0f))
            val b = Coin(id = 2, position = Vec2(500f + gap, centre.y), velocity = Vec2(-topSpeed * 2f, 0f))
            val run = runToRest(listOf(a, b))
            val (endA, endB) = run.coins
            assertTrue("coin 1 passed through coin 2 (gap $gap)", endA.position.x < endB.position.x)
            assertNoOverlaps(run.coins)
        }
    }

    @Test
    fun `the striker at twice top speed hits a coin in its path`() {
        val striker = Coin(id = STRIKER_ID, position = Vec2(centre.x, 650f), velocity = Vec2(0f, -topSpeed * 2f))
        val coin = Coin(id = 1, position = Vec2(centre.x, 450f))
        val run = runToRest(listOf(striker, coin))
        // The coin was struck: it moved off its spot (or went down), and the striker never got past it.
        val end = run.coins.first { it.id == 1 }
        assertTrue("the coin never moved", end.pocketed || end.position != coin.position)
        assertNoOverlaps(run.coins)
    }

    @Test
    fun `a coin at twice top speed into each pocket sinks`() {
        CarromBoard.corners.forEach { hole ->
            val coin = Coin(id = 1, position = centre, velocity = (hole - centre).normalizedOrZero() * topSpeed * 2f)
            assertEquals("hole at $hole", listOf(1), runToRest(listOf(coin)).pocketed)
        }
    }

    @Test
    fun `a full-power break comes to rest with nothing overlapping or off the board`() {
        val run = runToRest(CarromRules.launch(CarromBoard.initialSetup(), Vec2(0f, -1f), 1f))
        assertNoOverlaps(run.coins)
    }

    @Test
    fun `full-power strikes in every direction settle cleanly`() {
        val random = Random(4)
        var coins = runToRest(CarromRules.launch(CarromBoard.initialSetup(), Vec2(0f, -1f), 1f)).coins
        repeat(25) {
            val angle = random.nextDouble(0.0, 2 * Math.PI)
            val striker = Coin(
                id = STRIKER_ID,
                position = CarromBoard.baselineFor(1),
                velocity = Vec2(cos(angle).toFloat(), sin(angle).toFloat()) * topSpeed
            )
            val board = CarromPhysics.separate(coins.filter { it.id != STRIKER_ID } + striker)
            val run = runToRest(board)
            assertNoOverlaps(run.coins)
            coins = run.coins
        }
    }

    @Test
    fun `a full-power strike still comes to rest in good time`() {
        val striker = Coin(id = STRIKER_ID, position = CarromBoard.baselineFor(1), velocity = Vec2(0f, -topSpeed))
        val run = runToRest(listOf(striker), maxSeconds = 6f)
        assertTrue("came to rest after ${run.seconds}s", run.seconds < 6f)
    }
}
