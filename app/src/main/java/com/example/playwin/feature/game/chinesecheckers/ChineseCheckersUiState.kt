package com.example.playwin.feature.game.chinesecheckers

import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersDifficulty
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersMove
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersPosition
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersRules
import com.example.playwin.core.domain.chinesecheckers.DEFAULT_CHINESE_CHECKERS_DIFFICULTY

/**
 * The two sides' peg colours, each with its own starting point: Gold starts in
 * the bottom point (player 1), Ruby in the top one (player 2). In "Play vs
 * Computer" you pick one - Gold unless you choose otherwise - the computer
 * takes the other, and you always move first.
 */
enum class PegColor(val label: String, val player: Int) {
    GOLD("Gold", 1),
    RUBY("Ruby", 2);

    fun other(): PegColor = if (this == GOLD) RUBY else GOLD
}

/** The opening position with [human]'s side to move first. */
fun chineseCheckersStartFor(human: PegColor): ChineseCheckersPosition =
    ChineseCheckersRules.initialPosition().copy(sideToMove = human.player)

/** How long a peg takes over each hole it lands on - a step, or each jump of a chain - when a move is shown. */
const val CHINESE_CHECKERS_HOP_MILLIS = 170L

data class ChineseCheckersUiState(
    /** The pegs the player chose; the computer plays the other colour, from the opposite point. */
    val humanColor: PegColor = PegColor.GOLD,
    val position: ChineseCheckersPosition = chineseCheckersStartFor(humanColor),
    /** False while the player is still choosing: nothing can be moved yet. */
    val started: Boolean = false,
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
    /** Your player number in [position]: 1 from the bottom point, 2 from the top. */
    val humanPlayer: Int get() = humanColor.player
    val computerPlayer: Int get() = humanColor.other().player
    val isHumanTurn: Boolean get() = position.sideToMove == humanPlayer
    val isGameOver: Boolean get() = winner != null

    /** The difficulty can be changed until the first move is played. */
    val canChooseDifficulty: Boolean get() = moveCount == 0 && !isGameOver

    val legalTargets: List<Int> get() = pendingMoves.map { it.to }

    /** You can move a peg now. */
    val canPlay: Boolean get() = started && isHumanTurn && !isGameOver && !isResolving && !isComputerThinking

    fun pegsHome(player: Int): Int = ChineseCheckersRules.pegsInGoal(position, player)
}

/** How long the board takes to show [move] hopping into place. */
fun hopMillis(move: ChineseCheckersMove?): Long = (move?.path?.size ?: 0) * CHINESE_CHECKERS_HOP_MILLIS
