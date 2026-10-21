package com.example.playwin.core.domain.chinesecheckers

import kotlin.random.Random

/** How strongly the computer plays. */
enum class ChineseCheckersDifficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium")
}

val DEFAULT_CHINESE_CHECKERS_DIFFICULTY = ChineseCheckersDifficulty.MEDIUM

/**
 * The "Play vs Computer" opponent. It scores a position by how far its pegs
 * still have to go - each peg's distance to the far tip of its goal, plus
 * extra for the peg furthest behind, so nobody is left stranded at home -
 * and plays the move that gains most.
 *
 * Medium also looks one move further: a move is worth what it gains now,
 * plus most of the best move it sets up next turn (that's what finds jump
 * ladders), less part of the best jump it hands the opponent. Easy only looks
 * at the move in front of it and sometimes settles for a lesser one.
 *
 * The goal's 10 holes are exactly the 10 holes nearest its tip, so the best
 * possible score is a full goal: the bot packs its pegs in as it arrives.
 */
object ChineseCheckersBot {

    /** How much the peg furthest from goal counts on top of its plain distance. */
    private const val STRAGGLER_WEIGHT = 1.5

    /** What the next move's gain is worth now, for Medium. */
    private const val FOLLOW_UP_WEIGHT = 0.8

    /** How much of the opponent's best reply counts against a move, for Medium. */
    private const val OPPONENT_WEIGHT = 0.4

    /** How often Easy plays a lesser move, and from how many of the best it picks. */
    private const val EASY_SLIP_CHANCE = 0.35
    private const val EASY_SLIP_CHOICES = 4

    fun chooseMove(
        position: ChineseCheckersPosition,
        difficulty: ChineseCheckersDifficulty = DEFAULT_CHINESE_CHECKERS_DIFFICULTY,
        random: Random = Random
    ): ChineseCheckersMove? {
        val me = position.sideToMove
        val moves = ChineseCheckersRules.legalMoves(position)
        if (moves.isEmpty()) return null
        val before = distanceLeft(position, me)

        val scored = moves.map { move ->
            val after = ChineseCheckersRules.play(position, move)
            // A move that wins outright beats everything.
            if (ChineseCheckersRules.hasWon(after, me)) return move
            val gain = before - distanceLeft(after, me)
            val score = when (difficulty) {
                ChineseCheckersDifficulty.EASY -> gain
                ChineseCheckersDifficulty.MEDIUM ->
                    gain + FOLLOW_UP_WEIGHT * bestGain(ChineseCheckersRules.pass(after), me) - OPPONENT_WEIGHT * bestGain(after, ChineseCheckersBoard.opponent(me))
            }
            // A sliver of noise breaks ties, so equal moves don't always go the same way.
            move to score + random.nextDouble(0.0, 0.001)
        }.sortedByDescending { it.second }

        if (difficulty == ChineseCheckersDifficulty.EASY && random.nextDouble() < EASY_SLIP_CHANCE) {
            return scored.take(EASY_SLIP_CHOICES).random(random).first
        }
        return scored.first().first
    }

    /** How far [player]'s pegs still have to go; lower is better, and a full goal is the least possible. */
    internal fun distanceLeft(position: ChineseCheckersPosition, player: Int): Double {
        val tip = ChineseCheckersBoard.goalTip(player)
        var total = 0
        var furthest = 0
        for (i in position.cells.indices) {
            if (position.cells[i] != player) continue
            val d = ChineseCheckersBoard.holes[i].distanceTo(tip)
            total += d
            if (d > furthest) furthest = d
        }
        return total + STRAGGLER_WEIGHT * furthest
    }

    /** The most [player] could gain with one move from [position] ([player] to move). */
    private fun bestGain(position: ChineseCheckersPosition, player: Int): Double {
        val turn = if (position.sideToMove == player) position else ChineseCheckersRules.pass(position)
        val before = distanceLeft(turn, player)
        var best = 0.0
        for (move in ChineseCheckersRules.legalMoves(turn)) {
            val gain = before - distanceLeft(ChineseCheckersRules.play(turn, move), player)
            if (gain > best) best = gain
        }
        return best
    }
}
