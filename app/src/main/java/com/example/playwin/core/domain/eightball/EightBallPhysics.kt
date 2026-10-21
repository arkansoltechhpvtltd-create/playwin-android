package com.example.playwin.core.domain.eightball

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Table geometry, in logical units independent of screen pixels/density. */
object EightBallTable {
    const val WIDTH = 1000f
    const val HEIGHT = 500f
    /** About 1/11 of the table's short side - large enough to read the numbers on a phone. */
    const val BALL_RADIUS = 22f

    /**
     * A ball drops once its centre is this close to a corner. Kept in proportion to
     * the ball, so a ball running down the cushion drops as far from the corner,
     * relative to its size, as it always has.
     */
    const val POCKET_RADIUS = BALL_RADIUS * 2.25f

    const val CUE_START_X = WIDTH * 0.25f

    /** The head string, painted across the table where the cue ball starts; the "kitchen" is behind it. */
    const val HEAD_STRING_X = CUE_START_X
    const val RACK_APEX_X = WIDTH * 0.72f
    const val CENTER_Y = HEIGHT / 2f

    val corners: List<Vec2> = listOf(
        Vec2(0f, 0f),
        Vec2(WIDTH, 0f),
        Vec2(0f, HEIGHT),
        Vec2(WIDTH, HEIGHT)
    )

    /**
     * The catch radius of the two side pockets. Smaller than a corner's: a side
     * pocket's mouth is cut straight into the cushion, so with the corners' radius
     * every ball rolling along the rail past the middle would drop. At this size a
     * ball against the cushion drops within about a ball's width of the middle.
     */
    const val SIDE_POCKET_RADIUS = BALL_RADIUS * 1.6f

    /** The middle of each long cushion, in the mouth of the painted side pocket. */
    val sidePockets: List<Vec2> = listOf(
        Vec2(WIDTH / 2f, 0f),
        Vec2(WIDTH / 2f, HEIGHT)
    )

    /** All six pockets, each with the distance from its centre at which a ball drops. */
    val pockets: List<Pocket> =
        corners.map { Pocket(it, POCKET_RADIUS) } + sidePockets.map { Pocket(it, SIDE_POCKET_RADIUS) }

    /** Where to aim a ball to pot it: the centre of each of the six pockets. */
    val pocketCentres: List<Vec2> = pockets.map { it.center }

    /** Standard 15-ball triangle rack with the 8-ball on the middle spot, plus the cue ball. */
    fun initialRack(): List<Ball> {
        val rows = listOf(
            listOf(1),
            listOf(9, 2),
            listOf(10, 8, 3),
            listOf(11, 4, 12, 5),
            listOf(13, 6, 14, 7, 15)
        )
        // Balls a diameter apart in each row, and rows of a tight triangle
        // (√3/2 of that), so every neighbour just touches without overlapping.
        val spacing = BALL_RADIUS * 2f
        val rowSpacingX = spacing * sqrt(3f) / 2f

        val balls = mutableListOf(Ball(id = 0, position = Vec2(CUE_START_X, CENTER_Y)))
        rows.forEachIndexed { row, ids ->
            val rowHeight = (ids.size - 1) * spacing
            val startY = CENTER_Y - rowHeight / 2f
            ids.forEachIndexed { i, id ->
                balls += Ball(id = id, position = Vec2(RACK_APEX_X + row * rowSpacingX, startY + i * spacing))
            }
        }
        return balls
    }
}

/** A pocket: a ball drops once its centre comes within [radius] of [center]. */
data class Pocket(val center: Vec2, val radius: Float)

data class PhysicsStepResult(
    val balls: List<Ball>,
    val newlyPocketed: List<Int>,
    val anyMoving: Boolean,
    /** What happened on the table, in order: which balls touched, and which hit a cushion. The rules read fouls from it. */
    val events: List<TableEvent> = emptyList()
)

/** Something [EightBallPhysics] saw happen during a shot. */
sealed interface TableEvent {
    /** Balls [a] and [b] collided. */
    data class Contact(val a: Int, val b: Int) : TableEvent

    /** Ball [ballId] (the cue ball included) bounced off a cushion. */
    data class Cushion(val ballId: Int) : TableEvent
}

/** Simple arcade physics: friction, wall bounces, elastic ball-ball collisions, six pockets. */
object EightBallPhysics {
    // --- Speed and feel: tune these. ---

    /** The cue ball's launch speed at full power, in table units a second; power scales it linearly. */
    const val MAX_SHOT_SPEED = 2000f

    /** The share of its speed a ball loses each second (0..1). Lower carries further. */
    private const val FRICTION_PER_SECOND = 0.68f

    /**
     * A steady slow-down on top of [FRICTION_PER_SECOND], in units a second per
     * second. Friction alone fades a ball out slowly; this ends its last crawl
     * crisply, so every shot still comes to rest.
     */
    private const val ROLLING_DECELERATION = 45f

    // --- Physics. ---

    private const val MIN_SPEED = 3f
    private const val WALL_RESTITUTION = 0.9f
    private const val BALL_RESTITUTION = 0.98f

    /** The longest substep, however slowly things move. */
    private const val SUBSTEP_DT = 1f / 240f

    /**
     * The furthest any ball moves in one substep. Substeps shorten as balls
     * speed up, so at any speed a ball can't skip past a cushion, a pocket's
     * mouth or another ball between two checks.
     */
    const val MAX_TRAVEL_PER_SUBSTEP = EightBallTable.BALL_RADIUS / 4f

    /** How long [simulate] lets a shot run before calling it settled, as a guard against a never-quite-stopping table. */
    private const val MAX_SIMULATED_SECONDS = 12f
    private const val SIMULATION_FRAME = 1f / 60f

    /** Overlaps smaller than this are rounding, not contact: balls placed exactly touching stay put. */
    private const val CONTACT_SLOP = 0.01f

    /** Separation passes after each substep's collisions, and when balls come to rest (see [separate]). */
    private const val SEPARATION_PASSES = 3
    private const val SETTLE_PASSES = 32

    /**
     * Runs a shot to completion off-screen in fixed 1/60s frames and returns
     * where every ball came to rest plus every ball pocketed along the way.
     * Used by [EightBallBot] to try shots before committing to one.
     */
    fun simulate(balls: List<Ball>): PhysicsStepResult {
        var current = balls
        val pocketed = mutableListOf<Int>()
        val events = mutableListOf<TableEvent>()
        var elapsed = 0f
        while (elapsed < MAX_SIMULATED_SECONDS) {
            val result = step(current, SIMULATION_FRAME)
            current = result.balls
            pocketed += result.newlyPocketed
            events += result.events
            elapsed += SIMULATION_FRAME
            if (!result.anyMoving) return PhysicsStepResult(current, pocketed, anyMoving = false, events = events)
        }
        return PhysicsStepResult(
            current.map { if (it.pocketed) it else it.copy(velocity = Vec2.Zero) },
            pocketed,
            anyMoving = false,
            events = events
        )
    }

    fun step(balls: List<Ball>, dtSeconds: Float): PhysicsStepResult {
        var remaining = min(dtSeconds, 1f / 30f).coerceAtLeast(0f)

        var current = balls
        val newlyPocketed = mutableListOf<Int>()
        val events = mutableListOf<TableEvent>()
        while (remaining > 0f) {
            val subDt = substepLength(current, remaining)
            current = substep(current, subDt, newlyPocketed, events)
            remaining -= subDt
        }

        val anyMoving = current.any { !it.pocketed && it.velocity.length > MIN_SPEED }
        if (!anyMoving) {
            // Coming to rest: settle fully, so no two balls are left overlapping.
            current = separate(current.map { if (it.pocketed) it else it.copy(velocity = Vec2.Zero) })
        }
        return PhysicsStepResult(current, newlyPocketed, anyMoving, events)
    }

    /**
     * [balls] with every overlapping pair pushed apart along the line between
     * their centres until they just touch - half each way - and every ball kept
     * inside the cushions. A ball pushed against a cushion holds there and the
     * other moves on, so repeating converges; [passes] caps the repeats (each
     * pass at least halves what overlap is left). Two balls on the very same
     * spot are pushed apart sideways.
     */
    fun separate(balls: List<Ball>, passes: Int = SETTLE_PASSES): List<Ball> {
        val result = balls.toMutableList()
        val minDist = EightBallTable.BALL_RADIUS * 2f
        repeat(passes) {
            var moved = false
            for (i in result.indices) {
                if (result[i].pocketed) continue
                for (j in i + 1 until result.size) {
                    if (result[j].pocketed) continue
                    val a = result[i]
                    val b = result[j]
                    val delta = b.position - a.position
                    val dist = delta.length
                    if (dist >= minDist - CONTACT_SLOP) continue
                    val normal = if (dist < 0.0001f) Vec2(1f, 0f) else delta * (1f / dist)
                    val push = normal * ((minDist - dist) / 2f)
                    result[i] = a.copy(position = keepOnTable(a.position - push))
                    result[j] = b.copy(position = keepOnTable(b.position + push))
                    moved = true
                }
            }
            if (!moved) return result
        }
        return result
    }

    /**
     * The next substep: at most [SUBSTEP_DT] and what's [remaining] of the frame,
     * and short enough that the fastest ball moves no more than [MAX_TRAVEL_PER_SUBSTEP].
     */
    private fun substepLength(balls: List<Ball>, remaining: Float): Float {
        val fastest = balls.maxOfOrNull { if (it.pocketed) 0f else it.velocity.length } ?: 0f
        val bySpeed = if (fastest > 0f) MAX_TRAVEL_PER_SUBSTEP / fastest else SUBSTEP_DT
        return min(remaining, min(SUBSTEP_DT, bySpeed))
    }

    /** [velocity] after [dt] more of friction and [ROLLING_DECELERATION], [decay] being friction's share for [dt]. */
    private fun slowed(velocity: Vec2, dt: Float, decay: Float): Vec2 {
        val speed = velocity.length
        if (speed <= 0f) return Vec2.Zero
        val newSpeed = max(0f, speed * decay - ROLLING_DECELERATION * dt)
        return velocity * (newSpeed / speed)
    }

    /** [p] moved inside the cushions, a ball's radius in from each. */
    private fun keepOnTable(p: Vec2): Vec2 {
        val r = EightBallTable.BALL_RADIUS
        return Vec2(p.x.coerceIn(r, EightBallTable.WIDTH - r), p.y.coerceIn(r, EightBallTable.HEIGHT - r))
    }

    private fun substep(
        balls: List<Ball>,
        dt: Float,
        newlyPocketed: MutableList<Int>,
        events: MutableList<TableEvent>
    ): List<Ball> {
        val decay = (1.0 - FRICTION_PER_SECOND).toDouble().pow(dt.toDouble()).toFloat()

        val moved = balls.map { ball ->
            if (ball.pocketed) return@map ball
            val v = if (ball.velocity.length < MIN_SPEED) Vec2.Zero else ball.velocity
            ball.copy(position = ball.position + v * dt, velocity = slowed(v, dt, decay))
        }

        val afterPockets = moved.map { ball ->
            if (ball.pocketed) return@map ball
            val pocketed = EightBallTable.pockets.any { pocket -> (ball.position - pocket.center).length < pocket.radius }
            if (pocketed) {
                newlyPocketed += ball.id
                ball.copy(velocity = Vec2.Zero, pocketed = true)
            } else {
                ball
            }
        }

        val afterWalls = afterPockets.map { ball ->
            if (ball.pocketed) return@map ball
            val r = EightBallTable.BALL_RADIUS
            var x = ball.position.x
            var y = ball.position.y
            var vx = ball.velocity.x
            var vy = ball.velocity.y
            var bounced = false
            if (x - r < 0f) {
                x = r
                vx = -vx * WALL_RESTITUTION
                bounced = true
            }
            if (x + r > EightBallTable.WIDTH) {
                x = EightBallTable.WIDTH - r
                vx = -vx * WALL_RESTITUTION
                bounced = true
            }
            if (y - r < 0f) {
                y = r
                vy = -vy * WALL_RESTITUTION
                bounced = true
            }
            if (y + r > EightBallTable.HEIGHT) {
                y = EightBallTable.HEIGHT - r
                vy = -vy * WALL_RESTITUTION
                bounced = true
            }
            if (bounced) events += TableEvent.Cushion(ball.id)
            ball.copy(position = Vec2(x, y), velocity = Vec2(vx, vy))
        }

        // Resolving one collision can nudge a ball into a third; a few separation
        // passes clear what's left, so overlaps never build up while balls roll.
        return separate(resolveBallCollisions(afterWalls, events), SEPARATION_PASSES)
    }

    private fun resolveBallCollisions(balls: List<Ball>, events: MutableList<TableEvent>): List<Ball> {
        val result = balls.toMutableList()
        val minDist = EightBallTable.BALL_RADIUS * 2f

        for (i in result.indices) {
            if (result[i].pocketed) continue
            for (j in i + 1 until result.size) {
                if (result[j].pocketed) continue
                val a = result[i]
                val b = result[j]
                val delta = b.position - a.position
                val dist = delta.length
                // Balls just touching (a rack, or a ball at rest against another) aren't colliding.
                if (dist <= 0.0001f || dist >= minDist - CONTACT_SLOP) continue

                val normal = delta.normalizedOrZero()
                val overlap = minDist - dist
                val correction = normal * (overlap / 2f)
                val newAPos = a.position - correction
                val newBPos = b.position + correction

                val v1n = a.velocity.dot(normal)
                val v2n = b.velocity.dot(normal)
                if (v2n - v1n > 0f) {
                    // Already separating - just fix the overlap, leave velocities alone.
                    result[i] = a.copy(position = newAPos)
                    result[j] = b.copy(position = newBPos)
                    continue
                }

                events += TableEvent.Contact(a.id, b.id)
                val v1t = a.velocity - normal * v1n
                val v2t = b.velocity - normal * v2n
                val newV1n = v1n + (v2n - v1n) * BALL_RESTITUTION
                val newV2n = v2n + (v1n - v2n) * BALL_RESTITUTION

                result[i] = a.copy(position = newAPos, velocity = v1t + normal * newV1n)
                result[j] = b.copy(position = newBPos, velocity = v2t + normal * newV2n)
            }
        }
        return result
    }
}
