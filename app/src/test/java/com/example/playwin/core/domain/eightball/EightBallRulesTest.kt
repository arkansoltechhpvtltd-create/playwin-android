package com.example.playwin.core.domain.eightball

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EightBallRulesTest {

    private fun pocket(match: EightBallMatch, vararg ids: Int): List<Ball> =
        match.balls.map { if (it.id in ids) it.copy(pocketed = true) else it }

    @Test
    fun `the first group ball potted assigns groups and keeps the turn`() {
        val match = EightBallMatch()
        val outcome = EightBallRules.resolveShot(match, pocket(match, 11), listOf(11))
        assertEquals(mapOf(1 to BallGroup.STRIPES, 2 to BallGroup.SOLIDS), outcome.after.playerGroups)
        assertEquals(1, outcome.after.currentPlayer)
    }

    @Test
    fun `a scratch re-spots the cue ball and passes the turn`() {
        val match = EightBallMatch(playerGroups = mapOf(1 to BallGroup.SOLIDS, 2 to BallGroup.STRIPES))
        val outcome = EightBallRules.resolveShot(match, pocket(match, CUE_BALL_ID, 3), listOf(CUE_BALL_ID, 3))
        assertTrue(outcome.scratched)
        assertEquals(2, outcome.after.currentPlayer)
        val cue = outcome.after.balls.first { it.id == CUE_BALL_ID }
        assertFalse(cue.pocketed)
        assertEquals(Vec2(EightBallTable.CUE_START_X, EightBallTable.CENTER_Y), cue.position)
    }

    @Test
    fun `potting only the opponent's ball passes the turn`() {
        val match = EightBallMatch(playerGroups = mapOf(1 to BallGroup.SOLIDS, 2 to BallGroup.STRIPES))
        assertEquals(2, EightBallRules.resolveShot(match, pocket(match, 12), listOf(12)).after.currentPlayer)
    }

    @Test
    fun `the 8 wins only after clearing your group, and never with a scratch`() {
        val groups = mapOf(1 to BallGroup.SOLIDS, 2 to BallGroup.STRIPES)
        val solids = (1..7).toList().toIntArray()

        val cleared = EightBallMatch(balls = pocket(EightBallMatch(), *solids), playerGroups = groups)
        val legal = EightBallRules.resolveShot(cleared, pocket(cleared, EIGHT_BALL_ID), listOf(EIGHT_BALL_ID))
        assertEquals(1, EightBallRules.predictedWinner(legal))

        val scratch = EightBallRules.resolveShot(cleared, pocket(cleared, EIGHT_BALL_ID, CUE_BALL_ID), listOf(EIGHT_BALL_ID, CUE_BALL_ID))
        assertEquals(2, EightBallRules.predictedWinner(scratch))

        val early = EightBallMatch(playerGroups = groups)
        val tooSoon = EightBallRules.resolveShot(early, pocket(early, EIGHT_BALL_ID), listOf(EIGHT_BALL_ID))
        assertEquals(2, EightBallRules.predictedWinner(tooSoon))

        assertNull(EightBallRules.predictedWinner(EightBallRules.resolveShot(early, early.balls, emptyList())))
    }
}
