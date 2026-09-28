package com.example.playwin.core.domain.bingo

import kotlin.math.pow
import kotlin.random.Random

/**
 * The "Play vs Computer" bingo opponent. On its turn it may call any
 * uncalled number on its own card, or - like the human's "Call a random
 * number" button - one random uncalled number. It scores each option:
 *  - a call that completes its own line wins outright,
 *  - a call that would complete only the opponent's line is avoided,
 *  - otherwise it prefers numbers that push its most-advanced lines forward
 *    and that don't also push the opponent's lines forward.
 */
object BingoBot {

    fun chooseCall(
        myCard: List<List<Int>>,
        opponentCard: List<List<Int>>,
        called: Collection<Int>,
        random: Random = Random
    ): Int? {
        val randomOption = BingoRules.randomUncalled(called, random) ?: return null
        val candidates = (myCard.flatten().filter { it != 0 && it !in called } + randomOption).distinct()
        return candidates.maxBy { score(it, myCard, opponentCard, called) + random.nextDouble(0.0, 0.01) }
    }

    internal fun score(number: Int, myCard: List<List<Int>>, opponentCard: List<List<Int>>, called: Collection<Int>): Double {
        val after = called + number
        val iWin = BingoRules.hasLine(BingoRules.marks(myCard, after))
        val theyWin = BingoRules.hasLine(BingoRules.marks(opponentCard, after))
        return when {
            iWin -> 10_000.0
            theyWin -> -10_000.0
            else -> progress(number, myCard, called) - 0.9 * progress(number, opponentCard, called)
        }
    }

    /** How much calling [number] advances [card]'s lines: lines closer to complete count exponentially more. */
    private fun progress(number: Int, card: List<List<Int>>, called: Collection<Int>): Double {
        val marks = BingoRules.marks(card, called)
        return BingoRules.LINES
            .filter { line -> line.any { (r, c) -> card[r][c] == number } }
            .sumOf { line -> 3.0.pow(line.count { (r, c) -> marks[r][c] } + 1) }
    }
}
