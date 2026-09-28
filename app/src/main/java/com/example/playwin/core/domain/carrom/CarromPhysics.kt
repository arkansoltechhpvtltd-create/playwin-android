package com.example.playwin.core.domain.carrom

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Square carrom board geometry, in logical units independent of screen pixels/density. */
object CarromBoard {
    const val SIZE = 800f
    const val COIN_RADIUS = 15f
    const val STRIKER_RADIUS = 21f
    const val POCKET_RADIUS = 32f
    const val BASELINE_INSET = 90f
    private const val CENTER = SIZE / 2f

    val corners: List<Vec2> = listOf(
        Vec2(0f, 0f),
        Vec2(SIZE, 0f),
        Vec2(0f, SIZE),
        Vec2(SIZE, SIZE)
    )

    fun radiusOf(id: Int): Float = if (id == STRIKER_ID) STRIKER_RADIUS else COIN_RADIUS

    /** Player 1 strikes from the bottom baseline, player 2 from the top. */
    fun baselineFor(player: Int): Vec2 =
        if (player == 1) Vec2(CENTER, SIZE - BASELINE_INSET) else Vec2(CENTER, BASELINE_INSET)

    /** The traditional queen-in-the-middle, two-ring flower arrangement, plus a striker on player 1's baseline. */
    fun initialSetup(): List<Coin> {
        val coins = mutableListOf(Coin(id = STRIKER_ID, position = baselineFor(1)))
        coins += Coin(id = QUEEN_ID, position = Vec2(CENTER, CENTER))

        var whiteId = 1
        var blackId = 10
        fun nextId(index: Int): Int = if (index % 2 == 0) whiteId++ else blackId++

        val ring1Radius = COIN_RADIUS * 2.2f
        repeat(6) { i ->
            val angle = Math.toRadians((i * 60).toDouble())
            val pos = Vec2(CENTER + ring1Radius * cos(angle).toFloat(), CENTER + ring1Radius * sin(angle).toFloat())
            coins += Coin(id = nextId(i), position = pos)
        }

        val ring2Radius = COIN_RADIUS * 4.4f
        repeat(12) { i ->
            val angle = Math.toRadians((i * 30 + 15).toDouble())
            val pos = Vec2(CENTER + ring2Radius * cos(angle).toFloat(), CENTER + ring2Radius * sin(angle).toFloat())
            coins += Coin(id = nextId(i), position = pos)
        }

        return coins
    }
}

data class CarromStepResult(
    val coins: List<Coin>,
    val newlyPocketed: List<Int>,
    val anyMoving: Boolean
)

/** Simple arcade physics: friction, wall bounces, elastic collisions, corner pockets. */
object CarromPhysics {
    private const val FRICTION_PER_SECOND = 0.68f
    private const val MIN_SPEED = 3f
    private const val WALL_RESTITUTION = 0.88f
    private const val PIECE_RESTITUTION = 0.97f
    private const val SUBSTEP_DT = 1f / 240f
    const val MAX_SHOT_SPEED = 1500f

    /** How long [simulate] lets a shot run before calling it settled, as a guard against a never-quite-stopping board. */
    private const val MAX_SIMULATED_SECONDS = 12f
    private const val SIMULATION_FRAME = 1f / 60f

    /**
     * Runs a shot to completion off-screen in fixed 1/60s frames and returns
     * where everything came to rest plus every piece pocketed along the way.
     * Used by [CarromBot] to try shots before committing to one.
     */
    fun simulate(coins: List<Coin>): CarromStepResult {
        var current = coins
        val pocketed = mutableListOf<Int>()
        var elapsed = 0f
        while (elapsed < MAX_SIMULATED_SECONDS) {
            val result = step(current, SIMULATION_FRAME)
            current = result.coins
            pocketed += result.newlyPocketed
            elapsed += SIMULATION_FRAME
            if (!result.anyMoving) return CarromStepResult(current, pocketed, anyMoving = false)
        }
        return CarromStepResult(current.map { if (it.pocketed) it else it.copy(velocity = Vec2.Zero) }, pocketed, anyMoving = false)
    }

    fun step(coins: List<Coin>, dtSeconds: Float): CarromStepResult {
        val clampedDt = min(dtSeconds, 1f / 30f).coerceAtLeast(0f)
        val subSteps = max(1, ceil(clampedDt / SUBSTEP_DT).toInt())
        val subDt = clampedDt / subSteps

        var current = coins
        val newlyPocketed = mutableListOf<Int>()
        repeat(subSteps) {
            current = substep(current, subDt, newlyPocketed)
        }

        val anyMoving = current.any { !it.pocketed && it.velocity.length > MIN_SPEED }
        if (!anyMoving) {
            current = current.map { if (it.pocketed) it else it.copy(velocity = Vec2.Zero) }
        }
        return CarromStepResult(current, newlyPocketed, anyMoving)
    }

    private fun substep(coins: List<Coin>, dt: Float, newlyPocketed: MutableList<Int>): List<Coin> {
        val decay = (1.0 - FRICTION_PER_SECOND).toDouble().pow(dt.toDouble()).toFloat()

        val moved = coins.map { coin ->
            if (coin.pocketed) return@map coin
            val v = if (coin.velocity.length < MIN_SPEED) Vec2.Zero else coin.velocity
            coin.copy(position = coin.position + v * dt, velocity = v * decay)
        }

        val afterPockets = moved.map { coin ->
            if (coin.pocketed) return@map coin
            val pocketed = CarromBoard.corners.any { corner -> (coin.position - corner).length < CarromBoard.POCKET_RADIUS }
            if (pocketed) {
                newlyPocketed += coin.id
                coin.copy(velocity = Vec2.Zero, pocketed = true)
            } else {
                coin
            }
        }

        val afterWalls = afterPockets.map { coin ->
            if (coin.pocketed) return@map coin
            val r = CarromBoard.radiusOf(coin.id)
            var x = coin.position.x
            var y = coin.position.y
            var vx = coin.velocity.x
            var vy = coin.velocity.y
            if (x - r < 0f) {
                x = r
                vx = -vx * WALL_RESTITUTION
            }
            if (x + r > CarromBoard.SIZE) {
                x = CarromBoard.SIZE - r
                vx = -vx * WALL_RESTITUTION
            }
            if (y - r < 0f) {
                y = r
                vy = -vy * WALL_RESTITUTION
            }
            if (y + r > CarromBoard.SIZE) {
                y = CarromBoard.SIZE - r
                vy = -vy * WALL_RESTITUTION
            }
            coin.copy(position = Vec2(x, y), velocity = Vec2(vx, vy))
        }

        return resolveCollisions(afterWalls)
    }

    private fun resolveCollisions(coins: List<Coin>): List<Coin> {
        val result = coins.toMutableList()

        for (i in result.indices) {
            if (result[i].pocketed) continue
            for (j in i + 1 until result.size) {
                if (result[j].pocketed) continue
                val a = result[i]
                val b = result[j]
                val minDist = CarromBoard.radiusOf(a.id) + CarromBoard.radiusOf(b.id)
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
                    result[i] = a.copy(position = newAPos)
                    result[j] = b.copy(position = newBPos)
                    continue
                }

                val v1t = a.velocity - normal * v1n
                val v2t = b.velocity - normal * v2n
                val newV1n = v1n + (v2n - v1n) * PIECE_RESTITUTION
                val newV2n = v2n + (v1n - v2n) * PIECE_RESTITUTION

                result[i] = a.copy(position = newAPos, velocity = v1t + normal * newV1n)
                result[j] = b.copy(position = newBPos, velocity = v2t + normal * newV2n)
            }
        }
        return result
    }
}
