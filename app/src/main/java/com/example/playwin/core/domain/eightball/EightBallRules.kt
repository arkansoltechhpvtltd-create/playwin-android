package com.example.playwin.core.domain.eightball

const val CUE_BALL_ID = 0
const val EIGHT_BALL_ID = 8

/**
 * One 8-ball game in progress: the balls, whose shot it is, which group each
 * player owns (set by the first legal pot after the break), and whether the
 * next shot is the break.
 */
data class EightBallMatch(
    val balls: List<Ball> = EightBallTable.initialRack(),
    val currentPlayer: Int = 1,
    val playerGroups: Map<Int, BallGroup> = emptyMap(),
    val isBreak: Boolean = true
) {
    val opponent: Int get() = 3 - currentPlayer
}

/** Why a shot was a foul. Any foul passes the turn, and potting the 8 on one loses. */
enum class EightBallFoul(val label: String) {
    SCRATCH("scratch"),
    NO_CONTACT("cue ball hit nothing"),
    WRONG_BALL_FIRST("wrong ball hit first"),
    NO_RAIL("no ball reached a cushion")
}

/**
 * What one shot did, with exactly the facts the game engine needs
 * ([com.example.playwin.core.domain.model.GameSnapshot.EightBall]) to rule
 * on it, and the match [after] it for when the game goes on.
 *
 * @param shooterClearedGroup the shooter's whole group was already down before
 * this shot - the only time the 8 may legally be potted.
 */
data class EightBallShotOutcome(
    val shooter: Int,
    val after: EightBallMatch,
    val potted: List<Int>,
    val scratched: Boolean,
    val eightBallPocketed: Boolean,
    val shooterClearedGroup: Boolean,
    val foul: EightBallFoul? = null,
    val wasBreak: Boolean = false
) {
    val fouled: Boolean get() = foul != null
}

/**
 * 8-ball turn rules (WPA-style), shared by vs-Computer play, online play and
 * [EightBallBot]:
 *  - The table stays open after the break; the first group ball potted on a
 *    legal shot after it decides groups. An 8 potted on the break is
 *    re-spotted, and potting any ball on a clean break keeps the table.
 *  - After the break a shot is a foul if the cue ball is potted (scratch),
 *    hits nothing, first hits the wrong ball (the 8 on an open table; anything
 *    but your group, or the 8 once your group is cleared), or if nothing is
 *    potted and no ball reaches a cushion after that first contact.
 *  - Potting one of your own group on a legal shot earns another; a foul
 *    passes the turn, and a scratched cue ball is re-spotted.
 * Whether potting the 8 wins or loses is the game engine's call -
 * [predictedWinner] only mirrors that rule so the bot can plan.
 */
object EightBallRules {

    /** The table with the cue ball sent off along [direction] at [power] (0..1 of max speed). */
    fun launch(balls: List<Ball>, direction: Vec2, power: Float): List<Ball> {
        val speed = EightBallPhysics.MAX_SHOT_SPEED * power.coerceIn(0f, 1f)
        return balls.map { if (it.id == CUE_BALL_ID) it.copy(velocity = direction.normalizedOrZero() * speed) else it }
    }

    fun resolveShot(
        match: EightBallMatch,
        finalBalls: List<Ball>,
        potted: List<Int>,
        events: List<TableEvent>
    ): EightBallShotOutcome {
        val shooter = match.currentPlayer
        val scratched = CUE_BALL_ID in potted
        val pottedObjects = potted.filter { it != CUE_BALL_ID }
        val shooterGroupBefore = match.playerGroups[shooter]
        val clearedBefore = shooterGroupBefore != null &&
            match.balls.filter { ballGroupOf(it.id) == shooterGroupBefore }.all { it.pocketed }

        val foul = if (scratched) EightBallFoul.SCRATCH else if (match.isBreak) null else contactFoul(
            events, pottedObjects, shooterGroupBefore, clearedBefore
        )

        var groups = match.playerGroups
        if (groups.isEmpty() && !match.isBreak && foul == null) {
            pottedObjects.firstOrNull { it != EIGHT_BALL_ID }?.let(::ballGroupOf)?.let { group ->
                groups = mapOf(shooter to group, match.opponent to group.other())
            }
        }

        val keepsTable = if (match.isBreak) {
            !scratched && pottedObjects.isNotEmpty()
        } else {
            foul == null && groups[shooter]?.let { mine -> pottedObjects.any { ballGroupOf(it) == mine } } == true
        }
        val nextPlayer = if (keepsTable) shooter else match.opponent

        var settled = if (scratched) respotCueBall(finalBalls) else finalBalls
        val eightPotted = EIGHT_BALL_ID in potted
        if (eightPotted && match.isBreak) settled = respotEightBall(settled)

        return EightBallShotOutcome(
            shooter = shooter,
            after = EightBallMatch(settled, nextPlayer, groups, isBreak = false),
            potted = potted,
            scratched = scratched,
            eightBallPocketed = eightPotted,
            shooterClearedGroup = clearedBefore,
            foul = foul,
            wasBreak = match.isBreak
        )
    }

    /** The turn passes without a shot (e.g. the shot clock ran out). A break not yet taken passes to the other player. */
    fun passTurn(match: EightBallMatch): EightBallMatch = match.copy(currentPlayer = match.opponent)

    /**
     * Who wins because of this shot, mirroring the engine's rule: an 8 on the
     * break is re-spotted; otherwise potting the 8 wins only with your group
     * already cleared and no foul, and loses otherwise. Null if the game goes
     * on. For planning only.
     */
    fun predictedWinner(outcome: EightBallShotOutcome): Int? = when {
        !outcome.eightBallPocketed || outcome.wasBreak -> null
        outcome.shooterClearedGroup && !outcome.fouled -> outcome.shooter
        else -> 3 - outcome.shooter
    }

    private fun contactFoul(
        events: List<TableEvent>,
        pottedObjects: List<Int>,
        shooterGroup: BallGroup?,
        clearedBefore: Boolean
    ): EightBallFoul? {
        val firstIndex = events.indexOfFirst { it is TableEvent.Contact && (it.a == CUE_BALL_ID || it.b == CUE_BALL_ID) }
        if (firstIndex == -1) return EightBallFoul.NO_CONTACT
        val first = events[firstIndex] as TableEvent.Contact
        val firstHit = if (first.a == CUE_BALL_ID) first.b else first.a

        val legalFirst = when {
            shooterGroup == null -> firstHit != EIGHT_BALL_ID
            clearedBefore -> firstHit == EIGHT_BALL_ID
            else -> ballGroupOf(firstHit) == shooterGroup
        }
        if (!legalFirst) return EightBallFoul.WRONG_BALL_FIRST

        val railAfterContact = events.drop(firstIndex + 1).any { it is TableEvent.Cushion }
        if (pottedObjects.isEmpty() && !railAfterContact) return EightBallFoul.NO_RAIL
        return null
    }

    private fun respotCueBall(balls: List<Ball>): List<Ball> = balls.map {
        if (it.id == CUE_BALL_ID) {
            it.copy(position = Vec2(EightBallTable.CUE_START_X, EightBallTable.CENTER_Y), velocity = Vec2.Zero, pocketed = false)
        } else {
            it
        }
    }

    /** The 8 back on the foot spot, or the nearest free spot behind it along the table's long axis. */
    private fun respotEightBall(balls: List<Ball>): List<Ball> {
        val others = balls.filter { it.id != EIGHT_BALL_ID && !it.pocketed }
        val step = EightBallTable.BALL_RADIUS / 2f
        val spot = generateSequence(EightBallTable.RACK_APEX_X) { it + step }
            .takeWhile { it <= EightBallTable.WIDTH - EightBallTable.BALL_RADIUS }
            .map { Vec2(it, EightBallTable.CENTER_Y) }
            .firstOrNull { spot -> others.all { (it.position - spot).length >= EightBallTable.BALL_RADIUS * 2f } }
            ?: Vec2(EightBallTable.RACK_APEX_X, EightBallTable.CENTER_Y)
        return balls.map { if (it.id == EIGHT_BALL_ID) it.copy(position = spot, velocity = Vec2.Zero, pocketed = false) else it }
    }
}
