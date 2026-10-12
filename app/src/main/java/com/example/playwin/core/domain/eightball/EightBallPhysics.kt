package com.example.playwin.core.domain.eightball

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

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
    const val RACK_APEX_X = WIDTH * 0.72f
    const val CENTER_Y = HEIGHT / 2f

    val corners: List<Vec2> = listOf(
        Vec2(0f, 0f),
        Vec2(WIDTH, 0f),
        Vec2(0f, HEIGHT),
        Vec2(WIDTH, HEIGHT)
    )

    /** Standard 15-ball triangle rack with the 8-ball on the middle spot, plus the cue ball. */
    fun initialRack(): List<Ball> {
        val rows = listOf(
            listOf(1),
            listOf(9, 2),
            listOf(10, 8, 3),
            listOf(11, 4, 12, 5),
            listOf(13, 6, 14, 7, 15)
        )
        val spacing = BALL_RADIUS * 2.05f
        val rowSpacingX = spacing * 0.9f

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

data class PhysicsStepResult(
    val balls: List<Ball>,
    val newlyPocketed: List<Int>,
    val anyMoving: Boolean
)

/** Simple arcade physics: friction, wall bounces, elastic ball-ball collisions, corner pockets. */
object EightBallPhysics {
    private const val FRICTION_PER_SECOND = 0.72f
    private const val MIN_SPEED = 3f
    private const val WALL_RESTITUTION = 0.9f
    private const val BALL_RESTITUTION = 0.98f
    private const val SUBSTEP_DT = 1f / 240f
    const val MAX_SHOT_SPEED = 1400f

    /** How long [simulate] lets a shot run before calling it settled, as a guard against a never-quite-stopping table. */
    private const val MAX_SIMULATED_SECONDS = 12f
    private const val SIMULATION_FRAME = 1f / 60f

    /**
     * Runs a shot to completion off-screen in fixed 1/60s frames and returns
     * where every ball came to rest plus every ball pocketed along the way.
     * Used by [EightBallBot] to try shots before committing to one.
     */
    fun simulate(balls: List<Ball>): PhysicsStepResult {
        var current = balls
        val pocketed = mutableListOf<Int>()
        var elapsed = 0f
        while (elapsed < MAX_SIMULATED_SECONDS) {
            val result = step(current, SIMULATION_FRAME)
            current = result.balls
            pocketed += result.newlyPocketed
            elapsed += SIMULATION_FRAME
            if (!result.anyMoving) return PhysicsStepResult(current, pocketed, anyMoving = false)
        }
        return PhysicsStepResult(current.map { if (it.pocketed) it else it.copy(velocity = Vec2.Zero) }, pocketed, anyMoving = false)
    }

    fun step(balls: List<Ball>, dtSeconds: Float): PhysicsStepResult {
        val clampedDt = min(dtSeconds, 1f / 30f).coerceAtLeast(0f)
        val subSteps = max(1, ceil(clampedDt / SUBSTEP_DT).toInt())
        val subDt = clampedDt / subSteps

        var current = balls
        val newlyPocketed = mutableListOf<Int>()
        repeat(subSteps) {
            current = substep(current, subDt, newlyPocketed)
        }

        val anyMoving = current.any { !it.pocketed && it.velocity.length > MIN_SPEED }
        if (!anyMoving) {
            current = current.map { if (it.pocketed) it else it.copy(velocity = Vec2.Zero) }
        }
        return PhysicsStepResult(current, newlyPocketed, anyMoving)
    }

    private fun substep(balls: List<Ball>, dt: Float, newlyPocketed: MutableList<Int>): List<Ball> {
        val decay = (1.0 - FRICTION_PER_SECOND).toDouble().pow(dt.toDouble()).toFloat()

        val moved = balls.map { ball ->
            if (ball.pocketed) return@map ball
            val v = if (ball.velocity.length < MIN_SPEED) Vec2.Zero else ball.velocity
            ball.copy(position = ball.position + v * dt, velocity = v * decay)
        }

        val afterPockets = moved.map { ball ->
            if (ball.pocketed) return@map ball
            val pocketed = EightBallTable.corners.any { corner ->
                (ball.position - corner).length < EightBallTable.POCKET_RADIUS
            }
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
            if (x - r < 0f) {
                x = r
                vx = -vx * WALL_RESTITUTION
            }
            if (x + r > EightBallTable.WIDTH) {
                x = EightBallTable.WIDTH - r
                vx = -vx * WALL_RESTITUTION
            }
            if (y - r < 0f) {
                y = r
                vy = -vy * WALL_RESTITUTION
            }
            if (y + r > EightBallTable.HEIGHT) {
                y = EightBallTable.HEIGHT - r
                vy = -vy * WALL_RESTITUTION
            }
            ball.copy(position = Vec2(x, y), velocity = Vec2(vx, vy))
        }

        return resolveBallCollisions(afterWalls)
    }

    private fun resolveBallCollisions(balls: List<Ball>): List<Ball> {
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
                if (dist <= 0.0001f || dist >= minDist) continue

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
