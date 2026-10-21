package com.example.playwin.core.domain.eightball

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EightBallTableTest {

    private val r = EightBallTable.BALL_RADIUS

    /** Float rounding allowed on "exactly touching"; far below anything visible. */
    private val rounding = 0.01f

    private fun assertNoOverlaps(balls: List<Ball>) {
        val on = balls.filter { !it.pocketed }
        for (i in on.indices) for (j in i + 1 until on.size) {
            val dist = (on[i].position - on[j].position).length
            assertTrue("balls ${on[i].id} and ${on[j].id} overlap by ${2 * r - dist}", dist >= 2 * r - rounding)
        }
    }

    @Test
    fun `the rack and cue ball sit on the table, the rack's neighbours touching without overlapping`() {
        val balls = EightBallTable.initialRack()
        assertEquals(16, balls.size)
        balls.forEach { ball ->
            assertTrue("ball ${ball.id} off the felt", ball.position.x in r..EightBallTable.WIDTH - r)
            assertTrue("ball ${ball.id} off the felt", ball.position.y in r..EightBallTable.HEIGHT - r)
        }
        assertNoOverlaps(balls)
        // A tight rack: every ball touches at least two others (the apex has exactly two).
        val rack = balls.filter { it.id != CUE_BALL_ID }
        rack.forEach { ball ->
            val touching = rack.count { it.id != ball.id && kotlin.math.abs((it.position - ball.position).length - 2 * r) < rounding }
            assertTrue("ball ${ball.id} touches only $touching", touching >= 2)
        }
    }

    @Test
    fun `two overlapping balls are pushed apart until they just touch`() {
        val a = Ball(id = 1, position = Vec2(500f, 250f))
        val b = Ball(id = 2, position = Vec2(500f + r, 250f)) // half overlapping
        val separated = EightBallPhysics.separate(listOf(a, b))
        val dist = (separated[0].position - separated[1].position).length
        assertEquals(2 * r, dist, rounding)
        // Pushed along the line between them, half each way.
        assertEquals(500f - r / 2f, separated[0].position.x, rounding)
        assertEquals(250f, separated[0].position.y, rounding)
    }

    @Test
    fun `balls on the very same spot are pushed apart`() {
        val separated = EightBallPhysics.separate(listOf(Ball(1, Vec2(500f, 250f)), Ball(2, Vec2(500f, 250f))))
        assertNoOverlaps(separated)
    }

    @Test
    fun `a ball pressed against a cushion stays on the table while the other gives way`() {
        val wall = Ball(id = 1, position = Vec2(r, 250f))
        val pressing = Ball(id = 2, position = Vec2(r + r / 2f, 250f))
        val separated = EightBallPhysics.separate(listOf(wall, pressing))
        assertNoOverlaps(separated)
        separated.forEach { assertTrue("ball ${it.id} pushed into the cushion", it.position.x >= r - rounding) }
    }

    @Test
    fun `after a full break nothing is left overlapping`() {
        val match = EightBallMatch()
        val result = EightBallPhysics.simulate(EightBallRules.launch(match, Vec2(1f, 0f), 1f))
        assertNoOverlaps(result.balls)
    }

    @Test
    fun `a racked table stays put until it's struck`() {
        val result = EightBallPhysics.simulate(EightBallTable.initialRack())
        assertTrue(result.newlyPocketed.isEmpty())
        assertEquals(EightBallTable.initialRack().map { it.position }, result.balls.map { it.position })
    }

    @Test
    fun `a ball sent into each corner drops, one along the cushion short of the corner doesn't`() {
        val centre = Vec2(EightBallTable.WIDTH / 2f, EightBallTable.CENTER_Y)
        EightBallTable.corners.forEach { corner ->
            val ball = Ball(id = 3, position = centre, velocity = (corner - centre).normalizedOrZero() * 900f)
            assertEquals("corner $corner", listOf(3), EightBallPhysics.simulate(listOf(ball)).newlyPocketed)
        }
        // Rolling slowly along the top cushion, past the side pocket, it stops well before the corner.
        val slow = Ball(id = 3, position = Vec2(600f, r), velocity = Vec2(150f, 0f))
        val result = EightBallPhysics.simulate(listOf(slow))
        assertTrue(result.newlyPocketed.isEmpty())
        assertFalse(result.balls.single().pocketed)
    }

    @Test
    fun `there are six pockets - the four corners and the middle of each long cushion`() {
        assertEquals(6, EightBallTable.pockets.size)
        assertEquals(
            setOf(Vec2(EightBallTable.WIDTH / 2f, 0f), Vec2(EightBallTable.WIDTH / 2f, EightBallTable.HEIGHT)),
            EightBallTable.sidePockets.toSet()
        )
    }

    @Test
    fun `a ball sent into each side pocket drops`() {
        EightBallTable.sidePockets.forEach { side ->
            val start = Vec2(side.x, EightBallTable.CENTER_Y)
            val ball = Ball(id = 3, position = start, velocity = (side - start).normalizedOrZero() * 600f)
            assertEquals("side pocket $side", listOf(3), EightBallPhysics.simulate(listOf(ball)).newlyPocketed)
        }
    }

    @Test
    fun `a ball crossing the table clear of the cushion rolls past the side pocket`() {
        val ball = Ball(id = 3, position = Vec2(300f, r * 3), velocity = Vec2(600f, 0f))
        val result = EightBallPhysics.simulate(listOf(ball))
        assertTrue(result.newlyPocketed.isEmpty())
        assertTrue("it rolled on past the middle", result.balls.single().position.x > EightBallTable.WIDTH / 2f)
    }

    @Test
    fun `a ball along the cushion drops only near the middle of the side pocket`() {
        val near = Ball(id = 3, position = Vec2(EightBallTable.WIDTH / 2f + r, r), velocity = Vec2.Zero)
        assertEquals(listOf(3), EightBallPhysics.step(listOf(near), 1f / 60f).newlyPocketed)
        val wide = Ball(id = 3, position = Vec2(EightBallTable.WIDTH / 2f + r * 2f, r), velocity = Vec2.Zero)
        assertTrue(EightBallPhysics.step(listOf(wide), 1f / 60f).newlyPocketed.isEmpty())
    }
}
