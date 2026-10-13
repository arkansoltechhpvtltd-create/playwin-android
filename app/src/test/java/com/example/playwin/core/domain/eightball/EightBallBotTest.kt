package com.example.playwin.core.domain.eightball

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EightBallBotTest {

    private val cueStart = Vec2(250f, 250f)

    // On the straight line from the cue ball to the bottom-right pocket (1000, 500).
    private val easySpot = Vec2(850f, 450f)
    private val farSpot = Vec2(500f, 60f)

    /** Only the cue ball and the listed balls are on the table; player 1 owns [myGroup] (null = open table). */
    private fun table(vararg onTable: Pair<Int, Vec2>, myGroup: BallGroup? = BallGroup.SOLIDS): EightBallMatch {
        val positions = onTable.toMap()
        val balls = EightBallTable.initialRack().map { ball ->
            when {
                ball.id == CUE_BALL_ID -> ball.copy(position = cueStart)
                ball.id in positions -> ball.copy(position = positions.getValue(ball.id))
                else -> ball.copy(pocketed = true)
            }
        }
        val groups = myGroup?.let { mapOf(1 to it, 2 to it.other()) } ?: emptyMap()
        return EightBallMatch(balls = balls, currentPlayer = 1, playerGroups = groups, isBreak = false)
    }

    private fun play(match: EightBallMatch, shot: EightBallBot.Shot) =
        EightBallPhysics.simulate(EightBallRules.launch(match.balls, shot.direction, shot.power))

    @Test
    fun `pots an easy ball from its own group`() {
        val match = table(1 to easySpot, EIGHT_BALL_ID to farSpot)
        val result = play(match, EightBallBot.chooseShot(match, Random(0), aimErrorDegrees = 0.0))
        assertTrue("potted ${result.newlyPocketed}", 1 in result.newlyPocketed)
        assertFalse(CUE_BALL_ID in result.newlyPocketed)
    }

    @Test
    fun `never sinks the 8 while it still has balls of its own`() {
        // The 8 sits on the easy line; the bot's own ball is elsewhere.
        val match = table(EIGHT_BALL_ID to easySpot, 2 to farSpot)
        repeat(3) { seed ->
            val result = play(match, EightBallBot.chooseShot(match, Random(seed), aimErrorDegrees = 0.0))
            assertFalse(EIGHT_BALL_ID in result.newlyPocketed)
        }
    }

    @Test
    fun `sinks the 8 to win once its group is cleared`() {
        val match = table(EIGHT_BALL_ID to easySpot, 9 to farSpot)
        val result = play(match, EightBallBot.chooseShot(match, Random(0), aimErrorDegrees = 0.0))
        assertTrue(EIGHT_BALL_ID in result.newlyPocketed)
        assertFalse(CUE_BALL_ID in result.newlyPocketed)
    }

    @Test
    fun `sinking the 8 early is scored as a loss`() {
        val match = table(EIGHT_BALL_ID to easySpot, 2 to farSpot)
        val straightAtEight = EightBallBot.Shot((easySpot - cueStart).normalizedOrZero(), 0.9f)
        assertEquals(-1000.0, EightBallBot.evaluate(match, straightAtEight), 0.0)
    }

    @Test
    fun `targets follow the rules - no 8 on an open table, only the 8 once cleared`() {
        val open = table(1 to easySpot, 9 to farSpot, EIGHT_BALL_ID to Vec2(500f, 440f), myGroup = null)
        assertEquals(setOf(1, 9), EightBallBot.targets(open).map { it.id }.toSet())

        val cleared = table(EIGHT_BALL_ID to easySpot, 9 to farSpot)
        assertEquals(listOf(EIGHT_BALL_ID), EightBallBot.targets(cleared).map { it.id })
    }
}
