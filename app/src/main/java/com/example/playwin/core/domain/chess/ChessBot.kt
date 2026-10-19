package com.example.playwin.core.domain.chess

import kotlin.random.Random

/**
 * How strongly the computer plays. [maxDepth] is how many plies it searches
 * (plus captures until the position is quiet), [timeBudgetMillis] caps how
 * long one move may take, and [randomMarginCp] lets an easier opponent pick
 * any move within that many centipawns of the best.
 */
enum class ChessDifficulty(
    val label: String,
    internal val maxDepth: Int,
    internal val timeBudgetMillis: Long,
    /** False keeps to material and piece placement: no mobility, king safety or pawn structure. */
    internal val fullEvaluation: Boolean,
    internal val randomMarginCp: Int
) {
    /** Two plies, material and placement only, and some variety - but it still takes free pieces and guards its own. */
    EASY("Easy", maxDepth = 2, timeBudgetMillis = 500, fullEvaluation = false, randomMarginCp = 80),
    MEDIUM("Medium", maxDepth = 4, timeBudgetMillis = 1000, fullEvaluation = true, randomMarginCp = 0),

    /** As deep as it gets in the time budget - typically 5 to 7 plies on a phone. */
    HARD("Hard", maxDepth = 32, timeBudgetMillis = 1500, fullEvaluation = true, randomMarginCp = 0)
}

/** The difficulty a new game starts with. */
val DEFAULT_CHESS_DIFFICULTY = ChessDifficulty.MEDIUM

/**
 * The "Play vs Computer" chess opponent. It searches with [ChessSearch]
 * (alpha-beta minimax, quiescence, a positional evaluation) and only ever
 * plays a move [ChessEngine.legalMoves] allows - the rules engine stays the
 * authority on legality, and the result of the game is still ruled on by
 * the game engine repository, never here.
 */
object ChessBot {

    /**
     * The move to play for the side to move, or null if it has none
     * (checkmate or stalemate). [earlierPositions] are the game's
     * [ChessEngine.repetitionKey]s so far, to steer into or away from a
     * repetition draw; [timeBudgetMillis] defaults to the difficulty's.
     */
    fun chooseMove(
        position: ChessPosition,
        difficulty: ChessDifficulty = DEFAULT_CHESS_DIFFICULTY,
        earlierPositions: List<String> = emptyList(),
        random: Random = Random,
        timeBudgetMillis: Long = difficulty.timeBudgetMillis
    ): ChessMove? {
        val legal = ChessEngine.legalMoves(position)
        if (legal.size <= 1) return legal.firstOrNull()
        val choice = ChessSearch(position, earlierPositions, difficulty, random).bestMove(timeBudgetMillis) ?: return legal.first()
        return legal.firstOrNull { it.from == choice.from && it.to == choice.to && it.promotion == choice.promotion }
            ?: legal.first()
    }
}
