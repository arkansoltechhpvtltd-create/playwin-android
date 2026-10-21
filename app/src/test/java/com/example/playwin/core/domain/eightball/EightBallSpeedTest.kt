package com.example.playwin.core.domain.eightball

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * At the fastest a ball ever moves - a full break, and well past it - nothing
 * passes through a cushion, a pocket's mouth or another ball, nothing is left
 * overlapping, and every shot still comes to rest.
 */
class EightBallSpeedTest {

    private val r = EightBallTable.BALL_RADIUS

    /** The fastest the cue ball is ever struck: a full-power break. */
    private val topSpeed = EightBallPhysics.MAX_SHOT_SPEED * BREAK_SPEED_MULTIPLIER

    /** The longest frame [EightBallPhysics.step] takes - a slow phone's worst case. */
    private val longFrame = 1f / 30f

    private class Run(val balls: List<Ball>, val pocketed: List<Int>, val events: List<TableEvent>, val seconds: Float)

    /** Plays [balls] out in [longFrame] frames, checking every frame that each ball is on the felt. */
    private fun runToRest(balls: List<Ball>, maxSeconds: Float = 10f): Run {
        var current = balls
        val pocketed = mutableListOf<Int>()
        val events = mutableListOf<TableEvent>()
        var seconds = 0f
        while (seconds < maxSeconds) {
            val result = EightBallPhysics.step(current, longFrame)
            current = result.balls
            pocketed += result.newlyPocketed
            events += result.events
            seconds += longFrame
            current.filter { !it.pocketed }.forEach { ball ->
                assertTrue("ball ${ball.id} through a side cushion at ${ball.position}", ball.position.x in r - 0.01f..EightBallTable.WIDTH - r + 0.01f)
                assertTrue("ball ${ball.id} through an end cushion at ${ball.position}", ball.position.y in r - 0.01f..EightBallTable.HEIGHT - r + 0.01f)
            }
            if (!result.anyMoving) return Run(current, pocketed, events, seconds)
        }
        throw AssertionError("still moving after $maxSeconds s")
    }

    private fun assertNoOverlaps(balls: List<Ball>) {
        val on = balls.filter { !it.pocketed }
        for (i in on.indices) for (j in i + 1 until on.size) {
            val dist = (on[i].position - on[j].position).length
            assertTrue("balls ${on[i].id} and ${on[j].id} overlap by ${2 * r - dist}", dist >= 2 * r - 0.01f)
        }
    }

    @Test
    fun `no substep moves a ball further than a quarter of its radius`() {
        assertTrue(EightBallPhysics.MAX_TRAVEL_PER_SUBSTEP <= r / 4f)
    }

    @Test
    fun `two balls meeting head on at twice top speed bounce off rather than pass through`() {
        // Every starting gap, so no lucky spacing lets them land overlapping on some check.
        for (gap in 0 until 80 step 2) {
            val a = Ball(id = 1, position = Vec2(300f, EightBallTable.CENTER_Y), velocity = Vec2(topSpeed * 2f, 0f))
            val b = Ball(id = 2, position = Vec2(650f + gap, EightBallTable.CENTER_Y), velocity = Vec2(-topSpeed * 2f, 0f))
            val run = runToRest(listOf(a, b))
            assertTrue("no contact (gap $gap)", run.events.contains(TableEvent.Contact(1, 2)))
            val (endA, endB) = run.balls
            assertTrue("ball 1 passed through ball 2 (gap $gap)", endA.position.x < endB.position.x)
            assertNoOverlaps(run.balls)
        }
    }

    @Test
    fun `a ball fired at a lone ball at twice top speed hits it`() {
        val cue = Ball(id = CUE_BALL_ID, position = Vec2(200f, EightBallTable.CENTER_Y), velocity = Vec2(topSpeed * 2f, 0f))
        val target = Ball(id = 5, position = Vec2(500f, EightBallTable.CENTER_Y))
        val run = runToRest(listOf(cue, target))
        assertTrue(run.events.contains(TableEvent.Contact(CUE_BALL_ID, 5)))
        assertTrue("the target never moved", run.balls[1].position != target.position || run.pocketed.contains(5))
    }

    @Test
    fun `a ball at twice top speed into each pocket drops`() {
        val centre = Vec2(EightBallTable.WIDTH / 2f, EightBallTable.CENTER_Y)
        EightBallTable.pockets.forEach { pocket ->
            val ball = Ball(id = 3, position = centre, velocity = (pocket.center - centre).normalizedOrZero() * topSpeed * 2f)
            assertEquals("pocket ${pocket.center}", listOf(3), runToRest(listOf(ball)).pocketed)
        }
    }

    @Test
    fun `a ball running along the cushion at top speed still drops into the side pocket`() {
        // Hugging the top cushion, it crosses the side pocket's mouth in a fraction of a frame.
        val ball = Ball(id = 3, position = Vec2(200f, r), velocity = Vec2(topSpeed, 0f))
        assertEquals(listOf(3), runToRest(listOf(ball)).pocketed)
    }

    @Test
    fun `a full break comes to rest with nothing overlapping or off the table`() {
        val run = runToRest(EightBallRules.launch(EightBallMatch(), Vec2(1f, 0f), 1f))
        assertNoOverlaps(run.balls)
    }

    @Test
    fun `full-power shots in every direction settle cleanly`() {
        val random = Random(8)
        var balls = EightBallRules.launch(EightBallMatch(), Vec2(1f, 0f), 1f).let { runToRest(it).balls }
        repeat(25) {
            val cue = balls.firstOrNull { it.id == CUE_BALL_ID && !it.pocketed }
                ?: Ball(id = CUE_BALL_ID, position = Vec2(EightBallTable.CUE_START_X, EightBallTable.CENTER_Y))
            val angle = random.nextDouble(0.0, 2 * Math.PI)
            val shot = Vec2(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat()) * EightBallPhysics.MAX_SHOT_SPEED
            val table = EightBallPhysics.separate(balls.filter { it.id != CUE_BALL_ID } + cue.copy(pocketed = false, velocity = shot))
            val run = runToRest(table)
            assertNoOverlaps(run.balls)
            balls = run.balls
        }
    }

    @Test
    fun `a full-power shot still comes to rest in good time`() {
        // A lone ball struck at full power down the length of the table, on a slow phone's frame rate.
        val ball = Ball(id = 3, position = Vec2(100f, EightBallTable.CENTER_Y), velocity = Vec2(EightBallPhysics.MAX_SHOT_SPEED, 0f))
        val run = runToRest(listOf(ball), maxSeconds = 6f)
        assertTrue("came to rest after ${run.seconds}s", run.seconds < 6f)
    }
}
