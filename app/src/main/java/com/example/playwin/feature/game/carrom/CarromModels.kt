package com.example.playwin.feature.game.carrom

import kotlin.math.sqrt

/** Lightweight 2D vector for board-space physics math, independent of any UI framework type. */
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

enum class CoinColor {
    WHITE,
    BLACK,
    QUEEN;

    fun other(): CoinColor = if (this == WHITE) BLACK else WHITE
}

/** Which color a coin id belongs to; the striker (id 0) is not a coin. */
fun coinColorOf(id: Int): CoinColor = when {
    id in 1..9 -> CoinColor.WHITE
    id in 10..18 -> CoinColor.BLACK
    id == 19 -> CoinColor.QUEEN
    else -> throw IllegalArgumentException("Not a coin id: $id")
}

const val QUEEN_ID = 19
const val STRIKER_ID = 0

data class Coin(
    val id: Int,
    val position: Vec2,
    val velocity: Vec2 = Vec2.Zero,
    val pocketed: Boolean = false
)
