package com.example.playwin.core.domain.eightball

const val CUE_BALL_ID = 0
const val EIGHT_BALL_ID = 8

/** One 8-ball game in progress: the balls, whose shot it is, and which group each player owns (set by the first pot). */
data class EightBallMatch(
    val balls: List<Ball> = EightBallTable.initialRack(),
    val currentPlayer: Int = 1,
    val playerGroups: Map<Int, BallGroup> = emptyMap()
) {
    val opponent: Int get() = 3 - currentPlayer
}

/**
 * What one shot did, with exactly the facts the game engine needs
 * ([com.example.playwin.core.domain.model.GameSnapshot.EightBall]) to rule
 * on it, and the match [after] it for when the game goes on.
 */
data class EightBallShotOutcome(
    val shooter: Int,
    val after: EightBallMatch,
    val potted: List<Int>,
    val scratched: Boolean,
    val eightBallPocketed: Boolean,
    val shooterClearedGroup: Boolean
)

/**
 * 8-ball turn rules, shared by vs-Computer play, online play and
 * [EightBallBot]: the first group ball potted decides groups; potting one of
 * your own group (without scratching) earns another shot; a scratched cue
 * ball is re-spotted and passes the turn. Whether potting the 8 wins or
 * loses is the game engine's call - [predictedWinner] only mirrors that rule
 * so the bot can plan.
 */
object EightBallRules {

    /** The table with the cue ball sent off along [direction] at [power] (0..1 of max speed). */
    fun launch(balls: List<Ball>, direction: Vec2, power: Float): List<Ball> {
        val speed = EightBallPhysics.MAX_SHOT_SPEED * power.coerceIn(0f, 1f)
        return balls.map { if (it.id == CUE_BALL_ID) it.copy(velocity = direction.normalizedOrZero() * speed) else it }
    }

    fun resolveShot(match: EightBallMatch, finalBalls: List<Ball>, potted: List<Int>): EightBallShotOutcome {
        val shooter = match.currentPlayer
        val scratched = CUE_BALL_ID in potted

        var groups = match.playerGroups
        if (groups.isEmpty()) {
            potted.firstOrNull { it != CUE_BALL_ID && it != EIGHT_BALL_ID }?.let(::ballGroupOf)?.let { group ->
                groups = mapOf(shooter to group, match.opponent to group.other())
            }
        }

        val shooterGroup = groups[shooter]
        val shooterClearedGroup = shooterGroup != null &&
            finalBalls.filter { ballGroupOf(it.id) == shooterGroup }.all { it.pocketed }
        val pottedOwnBall = shooterGroup != null && potted.any { ballGroupOf(it) == shooterGroup }
        val nextPlayer = if (!scratched && pottedOwnBall) shooter else match.opponent

        val settled = if (scratched) respotCueBall(finalBalls) else finalBalls
        return EightBallShotOutcome(
            shooter = shooter,
            after = EightBallMatch(settled, nextPlayer, groups),
            potted = potted,
            scratched = scratched,
            eightBallPocketed = EIGHT_BALL_ID in potted,
            shooterClearedGroup = shooterClearedGroup
        )
    }

    /** The turn passes without a shot (e.g. the shot clock ran out). */
    fun passTurn(match: EightBallMatch): EightBallMatch = match.copy(currentPlayer = match.opponent)

    /**
     * Who wins because of this shot, mirroring the engine's rule: potting the
     * 8 wins only with your group already cleared and no scratch, and loses
     * otherwise. Null if the game goes on. For planning only.
     */
    fun predictedWinner(outcome: EightBallShotOutcome): Int? = when {
        !outcome.eightBallPocketed -> null
        outcome.shooterClearedGroup && !outcome.scratched -> outcome.shooter
        else -> 3 - outcome.shooter
    }

    private fun respotCueBall(balls: List<Ball>): List<Ball> = balls.map {
        if (it.id == CUE_BALL_ID) {
            it.copy(position = Vec2(EightBallTable.CUE_START_X, EightBallTable.CENTER_Y), velocity = Vec2.Zero, pocketed = false)
        } else {
            it
        }
    }
}
