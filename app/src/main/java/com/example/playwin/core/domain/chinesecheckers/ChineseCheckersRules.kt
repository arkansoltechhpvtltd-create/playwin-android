package com.example.playwin.core.domain.chinesecheckers

/**
 * Whose peg is in each hole of [ChineseCheckersBoard.holes] - 0 empty, else
 * the player (1 or 2) - whose turn it is, and how many turns (moves and
 * passes, both sides together) have been played.
 */
data class ChineseCheckersPosition(
    val cells: List<Int>,
    val sideToMove: Int = 1,
    val turnsPlayed: Int = 0
) {
    fun pegsOf(player: Int): List<Int> = cells.indices.filter { cells[it] == player }
}

/**
 * A move: the peg in [from] ends in [to]. [path] is every hole it lands on in
 * turn, ending at [to] - one hole for a step or a single jump, one per jump
 * for a chain.
 */
data class ChineseCheckersMove(
    val from: Int,
    val to: Int,
    val path: List<Int>,
    val isJump: Boolean
)

/**
 * Standard Chinese checkers for two players: a peg steps to an adjacent empty
 * hole, or jumps over any one adjacent peg - its own or the opponent's - into
 * the empty hole straight beyond, and may keep jumping in the same turn.
 * Nothing is ever captured. A player wins with all 10 pegs in the opposite
 * point - or, once both sides have had [ANTI_SPOILING_MOVES] moves, with that
 * point full and at least one of their pegs in it (see [hasWon]). Pure
 * functions; the game engine re-checks every move with these.
 */
object ChineseCheckersRules {

    /**
     * Moves each side gets before pegs left behind in their own home stop
     * blocking the opponent. Until then only a full set of your own 10 pegs
     * wins; after it, the opponent's stragglers count towards filling your goal.
     */
    const val ANTI_SPOILING_MOVES = 30

    fun initialPosition(): ChineseCheckersPosition {
        val cells = MutableList(ChineseCheckersBoard.holes.size) { 0 }
        for (player in 1..2) ChineseCheckersBoard.home(player).forEach { cells[it] = player }
        return ChineseCheckersPosition(cells, sideToMove = 1)
    }

    /**
     * Every move for the peg in [from], if it belongs to the side to move:
     * each empty neighbour, then every hole a chain of jumps can reach - each
     * by its shortest chain, and none twice.
     */
    fun movesFrom(position: ChineseCheckersPosition, from: Int): List<ChineseCheckersMove> {
        val cells = position.cells
        if (from !in cells.indices || cells[from] != position.sideToMove) return emptyList()
        val neighbours = ChineseCheckersBoard.neighbours
        val moves = mutableListOf<ChineseCheckersMove>()
        val reached = BooleanArray(cells.size)
        reached[from] = true

        for (next in neighbours[from]) {
            if (next >= 0 && cells[next] == 0) {
                reached[next] = true
                moves += ChineseCheckersMove(from, next, listOf(next), isJump = false)
            }
        }

        // Jump chains, breadth first. The moving peg has left [from], so it's
        // empty: nothing jumps over it, and landing back there goes nowhere.
        val pathTo = HashMap<Int, List<Int>>()
        pathTo[from] = emptyList()
        val queue = ArrayDeque(listOf(from))
        val landed = BooleanArray(cells.size)
        landed[from] = true
        while (queue.isNotEmpty()) {
            val at = queue.removeFirst()
            for (d in neighbours[at].indices) {
                val over = neighbours[at][d]
                if (over < 0 || over == from || cells[over] == 0) continue
                val land = neighbours[over][d]
                if (land < 0 || landed[land] || cells[land] != 0) continue
                landed[land] = true
                val path = pathTo.getValue(at) + land
                pathTo[land] = path
                queue.addLast(land)
                if (!reached[land]) {
                    reached[land] = true
                    moves += ChineseCheckersMove(from, land, path, isJump = true)
                }
            }
        }
        return moves
    }

    /** Every move the side to move has. */
    fun legalMoves(position: ChineseCheckersPosition): List<ChineseCheckersMove> =
        position.pegsOf(position.sideToMove).flatMap { movesFrom(position, it) }

    /** The legal move taking the side to move's peg from [from] to [to], or null if there isn't one. */
    fun legalMove(position: ChineseCheckersPosition, from: Int, to: Int): ChineseCheckersMove? =
        movesFrom(position, from).firstOrNull { it.to == to }

    /** [position] after [move] (one of its legal moves), with the turn passed. */
    fun play(position: ChineseCheckersPosition, move: ChineseCheckersMove): ChineseCheckersPosition {
        val cells = position.cells.toMutableList()
        cells[move.to] = cells[move.from]
        cells[move.from] = 0
        return ChineseCheckersPosition(cells, ChineseCheckersBoard.opponent(position.sideToMove), position.turnsPlayed + 1)
    }

    /** [position] with the turn passed and nothing moved - for a side with no move at all. */
    fun pass(position: ChineseCheckersPosition): ChineseCheckersPosition =
        position.copy(sideToMove = ChineseCheckersBoard.opponent(position.sideToMove), turnsPlayed = position.turnsPlayed + 1)

    /** How many of [player]'s pegs are in their goal. */
    fun pegsInGoal(position: ChineseCheckersPosition, player: Int): Int =
        ChineseCheckersBoard.goal(player).count { position.cells[it] == player }

    /**
     * [player] has won: all 10 of their pegs are in the opposite point - or,
     * once both sides have had [ANTI_SPOILING_MOVES] moves, that point is full
     * and at least one peg in it is theirs. The goal is the opponent's home, so
     * any opponent peg still there has never got out of the way; after that
     * many moves it no longer blocks the win (the anti-spoiling rule). Before
     * then it does, so nobody wins early by hopping into the first hole the
     * other side vacates.
     */
    fun hasWon(position: ChineseCheckersPosition, player: Int): Boolean {
        val mine = pegsInGoal(position, player)
        if (mine == ChineseCheckersBoard.PEGS_PER_PLAYER) return true
        if (mine == 0 || position.turnsPlayed < 2 * ANTI_SPOILING_MOVES) return false
        return ChineseCheckersBoard.goal(player).all { position.cells[it] != 0 }
    }
}
