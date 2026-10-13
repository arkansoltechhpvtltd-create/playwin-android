package com.example.playwin.core.domain.eightball

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EightBallRulesTest {

    private val groups = mapOf(1 to BallGroup.SOLIDS, 2 to BallGroup.STRIPES)

    private fun pocket(match: EightBallMatch, vararg ids: Int): List<Ball> =
        match.balls.map { if (it.id in ids) it.copy(pocketed = true) else it }

    /** A clean shot: the cue ball hits [first], then something reaches a cushion. */
    private fun cleanHit(first: Int): List<TableEvent> =
        listOf(TableEvent.Contact(CUE_BALL_ID, first), TableEvent.Cushion(first))

    private fun afterBreak(groups: Map<Int, BallGroup> = emptyMap()) = EightBallMatch(playerGroups = groups, isBreak = false)

    private fun shot(match: EightBallMatch, events: List<TableEvent>, vararg potted: Int) =
        EightBallRules.resolveShot(match, pocket(match, *potted), potted.toList(), events)

    // --- The break ---

    @Test
    fun `the table stays open after the break, and a pot on it keeps the table`() {
        val outcome = shot(EightBallMatch(), cleanHit(1), 11)
        assertTrue(outcome.after.playerGroups.isEmpty())
        assertEquals(1, outcome.after.currentPlayer)
        assertFalse(outcome.after.isBreak)
        assertNull(outcome.foul)
    }

    @Test
    fun `an empty break passes the turn without a foul`() {
        val outcome = shot(EightBallMatch(), cleanHit(1))
        assertEquals(2, outcome.after.currentPlayer)
        assertNull(outcome.foul)
    }

    @Test
    fun `an 8 potted on the break is re-spotted, not a loss`() {
        val outcome = shot(EightBallMatch(), cleanHit(1), EIGHT_BALL_ID)
        assertTrue(outcome.wasBreak)
        assertNull(EightBallRules.predictedWinner(outcome))
        assertFalse(outcome.after.balls.first { it.id == EIGHT_BALL_ID }.pocketed)
    }

    @Test
    fun `scratching on the break is a foul`() {
        val outcome = shot(EightBallMatch(), cleanHit(1), CUE_BALL_ID, 3)
        assertEquals(EightBallFoul.SCRATCH, outcome.foul)
        assertEquals(2, outcome.after.currentPlayer)
    }

    // --- Open table and groups ---

    @Test
    fun `the first legal pot after the break assigns groups and keeps the turn`() {
        val outcome = shot(afterBreak(), cleanHit(11), 11)
        assertEquals(mapOf(1 to BallGroup.STRIPES, 2 to BallGroup.SOLIDS), outcome.after.playerGroups)
        assertEquals(1, outcome.after.currentPlayer)
    }

    @Test
    fun `hitting the 8 first on an open table is a foul and assigns nothing`() {
        val outcome = shot(afterBreak(), cleanHit(EIGHT_BALL_ID), 11)
        assertEquals(EightBallFoul.WRONG_BALL_FIRST, outcome.foul)
        assertTrue(outcome.after.playerGroups.isEmpty())
        assertEquals(2, outcome.after.currentPlayer)
    }

    // --- Fouls ---

    @Test
    fun `a scratch re-spots the cue ball and passes the turn`() {
        val outcome = shot(afterBreak(groups), cleanHit(3), CUE_BALL_ID, 3)
        assertTrue(outcome.scratched)
        assertEquals(EightBallFoul.SCRATCH, outcome.foul)
        assertEquals(2, outcome.after.currentPlayer)
        val cue = outcome.after.balls.first { it.id == CUE_BALL_ID }
        assertFalse(cue.pocketed)
        assertEquals(Vec2(EightBallTable.CUE_START_X, EightBallTable.CENTER_Y), cue.position)
    }

    @Test
    fun `missing every ball is a foul`() {
        val outcome = shot(afterBreak(groups), listOf(TableEvent.Cushion(CUE_BALL_ID)))
        assertEquals(EightBallFoul.NO_CONTACT, outcome.foul)
        assertEquals(2, outcome.after.currentPlayer)
    }

    @Test
    fun `hitting the opponent's ball first is a foul, even if your own then drops`() {
        val outcome = shot(afterBreak(groups), cleanHit(12), 3)
        assertEquals(EightBallFoul.WRONG_BALL_FIRST, outcome.foul)
        assertEquals("a foul loses the table", 2, outcome.after.currentPlayer)
    }

    @Test
    fun `nothing potted and no cushion after contact is a foul`() {
        val outcome = shot(afterBreak(groups), listOf(TableEvent.Contact(CUE_BALL_ID, 3)))
        assertEquals(EightBallFoul.NO_RAIL, outcome.foul)
    }

    @Test
    fun `a cushion before contact doesn't count`() {
        val events = listOf(TableEvent.Cushion(CUE_BALL_ID), TableEvent.Contact(CUE_BALL_ID, 3))
        assertEquals(EightBallFoul.NO_RAIL, shot(afterBreak(groups), events).foul)
    }

    @Test
    fun `a pot satisfies the cushion rule`() {
        val outcome = shot(afterBreak(groups), listOf(TableEvent.Contact(CUE_BALL_ID, 3)), 3)
        assertNull(outcome.foul)
        assertEquals(1, outcome.after.currentPlayer)
    }

    @Test
    fun `a clean miss with a cushion is legal and just passes the turn`() {
        val outcome = shot(afterBreak(groups), cleanHit(3))
        assertNull(outcome.foul)
        assertEquals(2, outcome.after.currentPlayer)
    }

    @Test
    fun `potting only the opponent's ball passes the turn`() {
        assertEquals(2, shot(afterBreak(groups), cleanHit(3), 12).after.currentPlayer)
    }

    // --- The 8 ---

    private val solids = (1..7).toList().toIntArray()

    @Test
    fun `the 8 wins after clearing your group on a clean shot`() {
        val cleared = afterBreak(groups).let { it.copy(balls = pocket(it, *solids)) }
        assertEquals(1, EightBallRules.predictedWinner(shot(cleared, cleanHit(EIGHT_BALL_ID), EIGHT_BALL_ID)))
    }

    @Test
    fun `the 8 loses with a scratch`() {
        val cleared = afterBreak(groups).let { it.copy(balls = pocket(it, *solids)) }
        assertEquals(2, EightBallRules.predictedWinner(shot(cleared, cleanHit(EIGHT_BALL_ID), EIGHT_BALL_ID, CUE_BALL_ID)))
    }

    @Test
    fun `the 8 loses on any other foul too`() {
        val cleared = afterBreak(groups).let { it.copy(balls = pocket(it, *solids)) }
        // Cue ball strikes a stripe first, which then knocks the 8 in.
        val outcome = shot(cleared, cleanHit(12), EIGHT_BALL_ID)
        assertEquals(EightBallFoul.WRONG_BALL_FIRST, outcome.foul)
        assertEquals(2, EightBallRules.predictedWinner(outcome))
    }

    @Test
    fun `the 8 loses if potted before your group is cleared`() {
        val tooSoon = shot(afterBreak(groups), cleanHit(3), EIGHT_BALL_ID)
        assertEquals(2, EightBallRules.predictedWinner(tooSoon))
    }

    @Test
    fun `the 8 loses if potted on the same shot as your last group ball`() {
        val oneLeft = afterBreak(groups).let { it.copy(balls = pocket(it, *solids.dropLast(1).toIntArray())) }
        val outcome = shot(oneLeft, cleanHit(7), 7, EIGHT_BALL_ID)
        assertFalse(outcome.shooterClearedGroup)
        assertEquals(2, EightBallRules.predictedWinner(outcome))
    }

    @Test
    fun `once your group is cleared you must hit the 8 first`() {
        val cleared = afterBreak(groups).let { it.copy(balls = pocket(it, *solids)) }
        assertEquals(EightBallFoul.WRONG_BALL_FIRST, shot(cleared, cleanHit(12)).foul)
        assertNull(shot(cleared, cleanHit(EIGHT_BALL_ID)).foul)
    }

    @Test
    fun `no 8 potted, no result`() {
        assertNull(EightBallRules.predictedWinner(shot(afterBreak(groups), cleanHit(3))))
    }

    // --- Ball in hand ---

    @Test
    fun `a foul gives the next player ball in hand anywhere`() {
        val outcome = shot(afterBreak(groups), cleanHit(12))
        assertEquals(EightBallFoul.WRONG_BALL_FIRST, outcome.foul)
        assertEquals(2, outcome.after.currentPlayer)
        assertEquals(BallInHand.ANYWHERE, outcome.after.ballInHand)
    }

    @Test
    fun `a scratch on the break gives ball in hand behind the head string`() {
        val outcome = shot(EightBallMatch(), cleanHit(1), CUE_BALL_ID)
        assertEquals(BallInHand.BEHIND_HEAD_STRING, outcome.after.ballInHand)
    }

    @Test
    fun `a clean shot gives no ball in hand, and taking the next shot uses it up`() {
        assertNull(shot(afterBreak(groups), cleanHit(3)).after.ballInHand)
        val inHand = afterBreak(groups).copy(ballInHand = BallInHand.ANYWHERE)
        assertNull(shot(inHand, cleanHit(3)).after.ballInHand)
    }

    @Test
    fun `ball in hand not used is lost when the turn times out`() {
        val inHand = afterBreak(groups).copy(ballInHand = BallInHand.ANYWHERE)
        assertNull(EightBallRules.passTurn(inHand).ballInHand)
    }

    @Test
    fun `with ball in hand the cue ball can go on any open spot`() {
        val inHand = afterBreak(groups).copy(ballInHand = BallInHand.ANYWHERE)
        val spot = Vec2(600f, 100f)
        val placed = EightBallRules.placeCueBall(inHand, spot)
        assertEquals(spot, placed?.balls?.first { it.id == CUE_BALL_ID }?.position)
    }

    @Test
    fun `the cue ball can't go on a ball, over a pocket, or off the felt`() {
        val inHand = afterBreak(groups).copy(ballInHand = BallInHand.ANYWHERE)
        val r = EightBallTable.BALL_RADIUS
        val onABall = inHand.balls.first { it.id == 1 }.position
        assertNull(EightBallRules.placeCueBall(inHand, onABall))
        assertNull(EightBallRules.placeCueBall(inHand, Vec2(r + 5f, r + 5f)))
        assertNull("over a side pocket", EightBallRules.placeCueBall(inHand, Vec2(EightBallTable.WIDTH / 2f, r + 10f)))
        assertNull(EightBallRules.placeCueBall(inHand, Vec2(500f, r / 2f)))
        assertNull(EightBallRules.placeCueBall(inHand, Vec2(EightBallTable.WIDTH + 10f, 250f)))
    }

    @Test
    fun `after a break foul the cue ball must stay behind the head string`() {
        val kitchen = afterBreak().copy(ballInHand = BallInHand.BEHIND_HEAD_STRING)
        assertNull(EightBallRules.placeCueBall(kitchen, Vec2(EightBallTable.HEAD_STRING_X + 40f, 250f)))
        assertEquals(
            Vec2(EightBallTable.HEAD_STRING_X - 40f, 150f),
            EightBallRules.placeCueBall(kitchen, Vec2(EightBallTable.HEAD_STRING_X - 40f, 150f))?.balls?.first { it.id == CUE_BALL_ID }?.position
        )
    }

    @Test
    fun `without ball in hand the cue ball stays put`() {
        assertNull(EightBallRules.placeCueBall(afterBreak(groups), Vec2(600f, 100f)))
    }

    // --- The physics reports what the rules need ---

    @Test
    fun `the simulation reports the cue ball's first contact and the cushions after it`() {
        val match = EightBallMatch()
        val result = EightBallPhysics.simulate(EightBallRules.launch(match.balls, Vec2(1f, 0f), 1f))
        val first = result.events.filterIsInstance<TableEvent.Contact>().first { CUE_BALL_ID in setOf(it.a, it.b) }
        assertEquals("straight into the apex ball", 1, if (first.a == CUE_BALL_ID) first.b else first.a)
        assertTrue(result.events.any { it is TableEvent.Cushion })
    }
}
