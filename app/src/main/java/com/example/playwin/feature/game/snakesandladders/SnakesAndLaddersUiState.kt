package com.example.playwin.feature.game.snakesandladders

import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersMove
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersState

/** In "Play vs Computer" the human is player 0 (gold) and rolls first; the computer is player 1. */
const val SL_HUMAN = 0
const val SL_COMPUTER = 1

/** How long a move takes on the board, shared by the board's animation and the pacing that waits for it. */
object SnakesAndLaddersTiming {
    /** The die tumbles for this long before the token sets off. */
    const val DICE_SETTLE_MILLIS = 560L
    const val STEP_MILLIS = 150L
    const val JUMP_MILLIS = 700L

    /** From the roll appearing to the token coming to rest. */
    fun moveMillis(move: SnakesAndLaddersMove?): Long {
        if (move == null) return 0L
        val steps = move.landed - move.from
        val jump = if (move.to != move.landed) JUMP_MILLIS else 0L
        return DICE_SETTLE_MILLIS + steps * STEP_MILLIS + jump
    }
}

data class SnakesAndLaddersUiState(
    val game: SnakesAndLaddersState = SnakesAndLaddersState(),
    /** The roll just played, for the die, the board's animation and the note under the turn line. */
    val lastMove: SnakesAndLaddersMove? = null,
    /** Set once the engine has confirmed a win. */
    val winner: Int? = null,
    /** A roll is being thrown, played out on the board or confirmed. */
    val isBusy: Boolean = false,
    val wonCouponId: String? = null
) {
    val isHumanTurn: Boolean get() = game.turn == SL_HUMAN
    val canRoll: Boolean get() = winner == null && !isBusy && isHumanTurn
}
