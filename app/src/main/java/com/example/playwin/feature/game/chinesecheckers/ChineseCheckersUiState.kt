package com.example.playwin.feature.game.chinesecheckers

import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersDifficulty
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersMove
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersPosition
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersRules
import com.example.playwin.core.domain.chinesecheckers.DEFAULT_CHINESE_CHECKERS_DIFFICULTY

/** In "Play vs Computer" you're player 1: you start in the bottom point and move first. */
const val CHINESE_CHECKERS_HUMAN = 1
const val CHINESE_CHECKERS_COMPUTER = 2

/** How long a peg takes over each hole it lands on - a step, or each jump of a chain - when a move is shown. */
const val CHINESE_CHECKERS_HOP_MILLIS = 170L

data class ChineseCheckersUiState(
    val position: ChineseCheckersPosition = ChineseCheckersRules.initialPosition(),
    /** The peg you've picked up, if any. */
    val selected: Int? = null,
    /** Where the [selected] peg can go: every step and every jump landing, chains included. */
    val pendingMoves: List<ChineseCheckersMove> = emptyList(),
    /** The last move played, by either side, so the board can show it hop by hop. */
    val lastMove: ChineseCheckersMove? = null,
    /** Moves played so far; also tells the board one move from the next when two are identical. */
    val moveCount: Int = 0,
    val winner: Int? = null,
    val isResolving: Boolean = false,
    val isComputerThinking: Boolean = false,
    val difficulty: ChineseCheckersDifficulty = DEFAULT_CHINESE_CHECKERS_DIFFICULTY,
    val wonCouponId: String? = null
) {
    val isHumanTurn: Boolean get() = position.sideToMove == CHINESE_CHECKERS_HUMAN
    val isGameOver: Boolean get() = winner != null

    /** The difficulty can be changed until the first move is played. */
    val canChooseDifficulty: Boolean get() = moveCount == 0 && !isGameOver

    val legalTargets: List<Int> get() = pendingMoves.map { it.to }

    /** You can move a peg now. */
    val canPlay: Boolean get() = isHumanTurn && !isGameOver && !isResolving && !isComputerThinking

    fun pegsHome(player: Int): Int = ChineseCheckersRules.pegsInGoal(position, player)
}

/** How long the board takes to show [move] hopping into place. */
fun hopMillis(move: ChineseCheckersMove?): Long = (move?.path?.size ?: 0) * CHINESE_CHECKERS_HOP_MILLIS
