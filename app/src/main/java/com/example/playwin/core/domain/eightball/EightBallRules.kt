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
    val isBreak: Boolean = true,
    /** The player to shoot may place the cue ball first, after the opponent's foul; null if not. */
    val ballInHand: BallInHand? = null
) {
    val opponent: Int get() = 3 - currentPlayer
}

/** Where a player with ball in hand may put the cue ball. */
enum class BallInHand {
    /** Anywhere on the table: after a foul once the game is under way. */
    ANYWHERE,

    /** Only behind the head string (the "kitchen"): after a foul on the break. */
    BEHIND_HEAD_STRING
}

/** Why a shot was a foul. Any foul passes the turn, and potting the 8 on one loses. */
enum class EightBallFoul(val label: String) {
    SCRATCH("scratch"),
    NO_CONTACT("cue ball hit nothing"),
    WRONG_BALL_FIRST("wrong ball hit first"),
    NO_RAIL("no ball reached a cushion"),
    ILLEGAL_BREAK("break pocketed nothing and fewer than 4 balls reached a cushion")
}

/** On the break, at least this many numbered balls must reach a cushion, unless one is pocketed. */
const val BREAK_MIN_BALLS_TO_CUSHION = 4

/**
 * How much harder than any other shot the break can be struck. The table's
 * friction stops balls quickly; at normal top speed even a full break sends
 * only two balls to a cushion, so the break gets the extra pace it needs to
 * spread the rack (a full break then reaches 7+ cushions or pots a ball).
 * A full break is struck at 3500 units a second, already the fastest shot in
 * the game; raise this to break harder still.
 */
const val BREAK_SPEED_MULTIPLIER = 1.75f

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
 *    re-spotted, and potting any ball on a clean break keeps the table. A
 *    break must pocket a ball or drive at least [BREAK_MIN_BALLS_TO_CUSHION]
 *    numbered balls to a cushion; otherwise it's a foul, as is a scratch.
 *  - After the break a shot is a foul if the cue ball is potted (scratch),
 *    hits nothing, first hits the wrong ball (the 8 on an open table; anything
 *    but your group, or the 8 once your group is cleared), or if nothing is
 *    potted and no ball reaches a cushion after that first contact.
 *  - Potting one of your own group on a legal shot earns another; a foul
 *    passes the turn, and a scratched cue ball is re-spotted.
 *  - After a foul the incoming player has ball in hand: they may place the
 *    cue ball anywhere ([placeCueBall]) - behind the head string only, if
 *    the foul came on the break.
 * Whether potting the 8 wins or loses is the game engine's call -
 * [predictedWinner] only mirrors that rule so the bot can plan.
 */
object EightBallRules {

    /**
     * [match]'s table with the cue ball sent off along [direction] at [power]
     * (0..1 of max speed) - up to [BREAK_SPEED_MULTIPLIER] times harder on the
     * break, so a full break can spread the rack to the cushions.
     */
    fun launch(match: EightBallMatch, direction: Vec2, power: Float): List<Ball> {
        val top = EightBallPhysics.MAX_SHOT_SPEED * if (match.isBreak) BREAK_SPEED_MULTIPLIER else 1f
        val speed = top * power.coerceIn(0f, 1f)
        return match.balls.map { if (it.id == CUE_BALL_ID) it.copy(velocity = direction.normalizedOrZero() * speed) else it }
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

        val foul = when {
            scratched -> EightBallFoul.SCRATCH
            match.isBreak -> breakFoul(events, pottedObjects)
            else -> contactFoul(events, pottedObjects, shooterGroupBefore, clearedBefore)
        }

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

        // A foul always passes the turn, and the incoming player takes the cue ball in hand.
        val ballInHand = when {
            foul == null -> null
            match.isBreak -> BallInHand.BEHIND_HEAD_STRING
            else -> BallInHand.ANYWHERE
        }
        return EightBallShotOutcome(
            shooter = shooter,
            after = EightBallMatch(settled, nextPlayer, groups, isBreak = false, ballInHand = ballInHand),
            potted = potted,
            scratched = scratched,
            eightBallPocketed = eightPotted,
            shooterClearedGroup = clearedBefore,
            foul = foul,
            wasBreak = match.isBreak
        )
    }

    /**
     * The turn passes without a shot (e.g. the shot clock ran out). A break not
     * yet taken passes to the other player; ball in hand not used is lost.
     */
    fun passTurn(match: EightBallMatch): EightBallMatch = match.copy(currentPlayer = match.opponent, ballInHand = null)

    /**
     * The match with the cue ball placed at [at], for a player with ball in
     * hand - or null if they don't have it or the spot isn't allowed: off the
     * felt, over a pocket, touching another ball, or in front of the head
     * string when only the kitchen is open.
     */
    fun placeCueBall(match: EightBallMatch, at: Vec2): EightBallMatch? {
        val inHand = match.ballInHand ?: return null
        val r = EightBallTable.BALL_RADIUS
        if (at.x < r || at.x > EightBallTable.WIDTH - r || at.y < r || at.y > EightBallTable.HEIGHT - r) return null
        if (inHand == BallInHand.BEHIND_HEAD_STRING && at.x > EightBallTable.HEAD_STRING_X) return null
        if (EightBallTable.pockets.any { (it.center - at).length <= it.radius + r }) return null
        if (match.balls.any { it.id != CUE_BALL_ID && !it.pocketed && (it.position - at).length < 2 * r }) return null
        return match.copy(balls = withCueAt(match.balls, at))
    }

    /** [balls] with the cue ball standing at [at] - no checks; for replaying a placement already made. */
    fun withCueAt(balls: List<Ball>, at: Vec2): List<Ball> = balls.map {
        if (it.id == CUE_BALL_ID) it.copy(position = at, velocity = Vec2.Zero, pocketed = false) else it
    }

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

    /**
     * A legal break pockets a ball or drives at least [BREAK_MIN_BALLS_TO_CUSHION]
     * different numbered balls to a cushion; the cue ball's own bounces don't count.
     */
    private fun breakFoul(events: List<TableEvent>, pottedObjects: List<Int>): EightBallFoul? {
        if (pottedObjects.isNotEmpty()) return null
        val ballsToCushion = events.filterIsInstance<TableEvent.Cushion>().map { it.ballId }.filter { it != CUE_BALL_ID }.toSet()
        return if (ballsToCushion.size < BREAK_MIN_BALLS_TO_CUSHION) EightBallFoul.ILLEGAL_BREAK else null
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

    /**
     * The cue ball back on the table after a scratch: on its starting spot, or
     * - if a ball lies there - the nearest free spot along the head string,
     * then further back into the kitchen. Never on top of another ball.
     */
    private fun respotCueBall(balls: List<Ball>): List<Ball> {
        val r = EightBallTable.BALL_RADIUS
        val others = balls.filter { it.id != CUE_BALL_ID && !it.pocketed }
        fun isFree(spot: Vec2) = others.all { (it.position - spot).length >= 2 * r } &&
            EightBallTable.pockets.none { (it.center - spot).length <= it.radius + r }
        val step = r / 2f
        val spot = sequence {
            var x = EightBallTable.CUE_START_X
            while (x >= r) {
                yield(Vec2(x, EightBallTable.CENTER_Y))
                var offset = step
                while (offset <= EightBallTable.CENTER_Y - r) {
                    yield(Vec2(x, EightBallTable.CENTER_Y - offset))
                    yield(Vec2(x, EightBallTable.CENTER_Y + offset))
                    offset += step
                }
                x -= step
            }
        }.firstOrNull(::isFree) ?: Vec2(EightBallTable.CUE_START_X, EightBallTable.CENTER_Y)
        return withCueAt(balls, spot)
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
