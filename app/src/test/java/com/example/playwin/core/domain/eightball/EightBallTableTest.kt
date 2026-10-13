package com.example.playwin.core.domain.eightball

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EightBallTableTest {

    private val r = EightBallTable.BALL_RADIUS

    @Test
    fun `the rack and cue ball sit on the table without touching a rail or each other`() {
        val balls = EightBallTable.initialRack()
        assertEquals(16, balls.size)
        balls.forEach { ball ->
            assertTrue("ball ${ball.id} off the felt", ball.position.x in r..EightBallTable.WIDTH - r)
            assertTrue("ball ${ball.id} off the felt", ball.position.y in r..EightBallTable.HEIGHT - r)
        }
        for (i in balls.indices) for (j in i + 1 until balls.size) {
            assertTrue("balls ${balls[i].id} and ${balls[j].id} overlap", (balls[i].position - balls[j].position).length >= 2 * r)
        }
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
