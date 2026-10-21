package com.example.playwin.core.domain.chinesecheckers

import kotlin.math.abs

/**
 * One hole on the star, in axial hex coordinates: [r] is the row (negative
 * towards the top of the screen) and [q] runs along it. The third axis [s]
 * makes q + r + s = 0, which is what keeps the six directions symmetric.
 */
data class Hole(val q: Int, val r: Int) {
    val s: Int get() = -q - r

    /** The fewest single steps from here to [other], ignoring pegs in the way. */
    fun distanceTo(other: Hole): Int = (abs(q - other.q) + abs(r - other.r) + abs(s - other.s)) / 2
}

/**
 * The classic 121-hole six-pointed star: two big triangles laid over each
 * other - every hole with q, r and s all at most [SIZE], plus every hole with
 * them all at least -[SIZE]. Their overlap is the 61-hole hexagon in the
 * middle; each of the six points is a triangle of 10 holes.
 *
 * Two players use opposite points. Player 1 (you, against the computer)
 * starts in the bottom point and races for the top; player 2 starts at the
 * top and races for the bottom.
 */
object ChineseCheckersBoard {
    /** The middle hexagon's radius. Each point of the star is this many rows deep. */
    const val SIZE = 4
    const val PEGS_PER_PLAYER = 10

    /** Every hole, row by row from the top, left to right along each row. */
    val holes: List<Hole> = buildList {
        for (r in -2 * SIZE..2 * SIZE) for (q in -2 * SIZE..2 * SIZE) {
            val s = -q - r
            val inUpperTriangle = q <= SIZE && r <= SIZE && s <= SIZE
            val inLowerTriangle = q >= -SIZE && r >= -SIZE && s >= -SIZE
            if (inUpperTriangle || inLowerTriangle) add(Hole(q, r))
        }
    }

    private val indexByHole: Map<Hole, Int> = holes.withIndex().associate { (i, hole) -> hole to i }

    /** The index of [hole] in [holes], or null if it's off the star. */
    fun indexOf(hole: Hole): Int? = indexByHole[hole]

    /** The six directions from a hole to its neighbours. */
    val directions: List<Hole> = listOf(Hole(1, 0), Hole(-1, 0), Hole(0, 1), Hole(0, -1), Hole(1, -1), Hole(-1, 1))

    /** neighbours[i][d]: the hole one step from hole i in direction d, or -1 off the star. */
    val neighbours: Array<IntArray> = Array(holes.size) { i ->
        val hole = holes[i]
        IntArray(directions.size) { d -> indexOf(Hole(hole.q + directions[d].q, hole.r + directions[d].r)) ?: -1 }
    }

    private val bottomPoint: Set<Int> = holes.indices.filter { holes[it].r > SIZE }.toSet()
    private val topPoint: Set<Int> = holes.indices.filter { holes[it].r < -SIZE }.toSet()

    /** The other player of two. */
    fun opponent(player: Int): Int = 3 - player

    /** The point [player]'s pegs start in. */
    fun home(player: Int): Set<Int> = if (player == 1) bottomPoint else topPoint

    /** The point [player] must fill to win: the opponent's home. */
    fun goal(player: Int): Set<Int> = home(opponent(player))

    /** The far tip of [player]'s goal - the hole their pegs are racing towards. */
    fun goalTip(player: Int): Hole = if (player == 1) Hole(SIZE, -2 * SIZE) else Hole(-SIZE, 2 * SIZE)
}
