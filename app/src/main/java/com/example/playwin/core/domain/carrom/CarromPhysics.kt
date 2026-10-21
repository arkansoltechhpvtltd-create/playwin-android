package com.example.playwin.core.domain.carrom

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
    private const val CENTER = SIZE / 2f

    // Where things are painted on carrom_board.png (1080px square), as fractions
    // (0..1) of the whole image. The board's walls are the inside of its gold rail;
    // everything else is mapped through that into board units.
    /** The playing surface inside the rail: its top-left corner (the art is square and centred) and its size. */
    const val ART_SURFACE_START = 66.5f / 1080f
    const val ART_SURFACE_SIZE = 947f / 1080f
    private const val ART_POCKET_NEAR = 0.111f
    private const val ART_POCKET_FAR = 0.889f
    /**
     * The catch radius: a piece sinks once its centre is this close to a pocket's
     * centre, as a fraction of the image. It matches the painted hole (0.036);
     * tune it here - smaller makes potting harder (0.032 is the next step down).
     */
    const val ART_POCKET_RADIUS = 0.036f
    /** The line through the bottom baseline's circles; the top one mirrors it. */
    private const val ART_BASELINE = 868f / 1080f
    /** The baseline's end circles, either side of the centre. */
    private const val ART_BASELINE_HALF_LENGTH = 330f / 1080f

    private fun fromArt(fraction: Float): Float = (fraction - ART_SURFACE_START) / ART_SURFACE_SIZE * SIZE

    /** [ART_POCKET_RADIUS] in board units. */
    const val POCKET_RADIUS = ART_POCKET_RADIUS / ART_SURFACE_SIZE * SIZE

    /** From each side's frame to the centre of its painted baseline. */
    const val BASELINE_INSET = SIZE - (ART_BASELINE - ART_SURFACE_START) / ART_SURFACE_SIZE * SIZE

    /** From the centre of a baseline to the centre of either of its end circles. */
    const val BASELINE_HALF_LENGTH = ART_BASELINE_HALF_LENGTH / ART_SURFACE_SIZE * SIZE

    /**
     * How far the striker may sit either side of its baseline's centre, in board
     * units: up to a striker radius inside the painted baseline's end circles.
     */
    const val STRIKER_SLIDE_HALF_SPAN = BASELINE_HALF_LENGTH - STRIKER_RADIUS

    /** Pocket centres, under the painted holes: top-left, top-right, bottom-left, bottom-right. */
    val corners: List<Vec2> = listOf(
        Vec2(fromArt(ART_POCKET_NEAR), fromArt(ART_POCKET_NEAR)),
        Vec2(fromArt(ART_POCKET_FAR), fromArt(ART_POCKET_NEAR)),
        Vec2(fromArt(ART_POCKET_NEAR), fromArt(ART_POCKET_FAR)),
        Vec2(fromArt(ART_POCKET_FAR), fromArt(ART_POCKET_FAR))
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
    // --- Speed and feel: tune these. ---

    /** The striker's launch speed at full power, in board units a second; power scales it linearly. */
    const val MAX_SHOT_SPEED = 2100f

    /** The share of its speed a piece loses each second (0..1). Lower carries further. */
    private const val FRICTION_PER_SECOND = 0.64f

    /**
     * A steady slow-down on top of [FRICTION_PER_SECOND], in units a second per
     * second. Friction alone fades a piece out slowly; this ends its last crawl
     * crisply, so every shot still comes to rest.
     */
    private const val ROLLING_DECELERATION = 45f

    // --- Physics. ---

    private const val MIN_SPEED = 3f
    private const val WALL_RESTITUTION = 0.88f
    private const val PIECE_RESTITUTION = 0.97f

    /** The longest substep, however slowly things move. */
    private const val SUBSTEP_DT = 1f / 240f

    /**
     * The furthest any piece moves in one substep - a quarter of the smaller
     * piece, a coin. Substeps shorten as pieces speed up, so at any speed a piece
     * can't skip past a wall, a pocket or another piece between two checks.
     */
    const val MAX_TRAVEL_PER_SUBSTEP = CarromBoard.COIN_RADIUS / 4f

    /** How long [simulate] lets a shot run before calling it settled, as a guard against a never-quite-stopping board. */
    private const val MAX_SIMULATED_SECONDS = 12f
    private const val SIMULATION_FRAME = 1f / 60f

    /** Overlaps smaller than this are rounding, not contact: pieces placed exactly touching stay put. */
    private const val CONTACT_SLOP = 0.01f

    /** Separation passes after each substep's collisions, and when pieces come to rest (see [separate]). */
    private const val SEPARATION_PASSES = 3
    private const val SETTLE_PASSES = 32

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
        var remaining = min(dtSeconds, 1f / 30f).coerceAtLeast(0f)

        var current = coins
        val newlyPocketed = mutableListOf<Int>()
        while (remaining > 0f) {
            val subDt = substepLength(current, remaining)
            current = substep(current, subDt, newlyPocketed)
            remaining -= subDt
        }

        val anyMoving = current.any { !it.pocketed && it.velocity.length > MIN_SPEED }
        if (!anyMoving) {
            // Coming to rest: settle fully, so no two pieces are left overlapping.
            current = separate(current.map { if (it.pocketed) it else it.copy(velocity = Vec2.Zero) })
        }
        return CarromStepResult(current, newlyPocketed, anyMoving)
    }

    /**
     * [coins] with every overlapping pair pushed apart along the line between
     * their centres until they just touch - half each way - and every piece kept
     * inside the walls. A piece pushed against a wall holds there and the other
     * moves on, so repeating converges; [passes] caps the repeats. Two pieces on
     * the very same spot are pushed apart sideways.
     */
    fun separate(coins: List<Coin>, passes: Int = SETTLE_PASSES): List<Coin> {
        val result = coins.toMutableList()
        repeat(passes) {
            var moved = false
            for (i in result.indices) {
                if (result[i].pocketed) continue
                for (j in i + 1 until result.size) {
                    if (result[j].pocketed) continue
                    val a = result[i]
                    val b = result[j]
                    val minDist = CarromBoard.radiusOf(a.id) + CarromBoard.radiusOf(b.id)
                    val delta = b.position - a.position
                    val dist = delta.length
                    if (dist >= minDist - CONTACT_SLOP) continue
                    val normal = if (dist < 0.0001f) Vec2(1f, 0f) else delta * (1f / dist)
                    val push = normal * ((minDist - dist) / 2f)
                    result[i] = a.copy(position = keepOnBoard(a.position - push, CarromBoard.radiusOf(a.id)))
                    result[j] = b.copy(position = keepOnBoard(b.position + push, CarromBoard.radiusOf(b.id)))
                    moved = true
                }
            }
            if (!moved) return result
        }
        return result
    }

    /**
     * The next substep: at most [SUBSTEP_DT] and what's [remaining] of the frame,
     * and short enough that the fastest piece moves no more than [MAX_TRAVEL_PER_SUBSTEP].
     */
    private fun substepLength(coins: List<Coin>, remaining: Float): Float {
        val fastest = coins.maxOfOrNull { if (it.pocketed) 0f else it.velocity.length } ?: 0f
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

    /** [p] moved inside the walls, [radius] in from each. */
    private fun keepOnBoard(p: Vec2, radius: Float): Vec2 =
        Vec2(p.x.coerceIn(radius, CarromBoard.SIZE - radius), p.y.coerceIn(radius, CarromBoard.SIZE - radius))

    private fun substep(coins: List<Coin>, dt: Float, newlyPocketed: MutableList<Int>): List<Coin> {
        val decay = (1.0 - FRICTION_PER_SECOND).toDouble().pow(dt.toDouble()).toFloat()

        val moved = coins.map { coin ->
            if (coin.pocketed) return@map coin
            val v = if (coin.velocity.length < MIN_SPEED) Vec2.Zero else coin.velocity
            coin.copy(position = coin.position + v * dt, velocity = slowed(v, dt, decay))
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

        // Resolving one collision can nudge a piece into a third; a few separation
        // passes clear what's left, so overlaps never build up while pieces slide.
        return separate(resolveCollisions(afterWalls), SEPARATION_PASSES)
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
                // Pieces just touching (or at rest against each other) aren't colliding.
                if (dist <= 0.0001f || dist >= minDist - CONTACT_SLOP) continue

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
