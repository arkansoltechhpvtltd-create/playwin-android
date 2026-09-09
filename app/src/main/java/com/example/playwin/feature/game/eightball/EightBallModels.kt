package com.example.playwin.feature.game.eightball

import kotlin.math.sqrt

/** Lightweight 2D vector for table-space physics math, independent of any UI framework type. */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    val length: Float get() = sqrt(x * x + y * y)
    fun dot(o: Vec2) = x * o.x + y * o.y

    fun normalizedOrZero(): Vec2 {
        val len = length
        return if (len < 0.0001f) Zero else Vec2(x / len, y / len)
    }

    companion object {
        val Zero = Vec2(0f, 0f)
    }
}

enum class BallGroup {
    SOLIDS,
    STRIPES;

    fun other(): BallGroup = if (this == SOLIDS) STRIPES else SOLIDS
}

/** Which group a numbered ball belongs to; null for the cue ball (0) and the 8-ball. */
fun ballGroupOf(id: Int): BallGroup? = when {
    id in 1..7 -> BallGroup.SOLIDS
    id in 9..15 -> BallGroup.STRIPES
    else -> null
}

data class Ball(
    val id: Int,
    val position: Vec2,
    val velocity: Vec2 = Vec2.Zero,
    val pocketed: Boolean = false
)
