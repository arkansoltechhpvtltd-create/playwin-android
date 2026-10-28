package com.example.playwin.feature.game.snakesandladders

import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersMove
import androidx.compose.ui.graphics.Color
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersState
import com.example.playwin.feature.game.FirstTurn

/** In "Play vs Computer" the human is player 0 and the computer player 1; either may roll first. */
const val SL_HUMAN = 0
const val SL_COMPUTER = 1

/** The token colours the player can pick from, each a glossy pawn's highlight, body and shade. */
enum class SlTokenColor(val label: String, val colors: List<Color>) {
    GOLD("Gold", SlGoldToken),
    PEARL("Pearl", SlPearlToken),
    RUBY("Ruby", SlRubyToken),
    SAPPHIRE("Sapphire", SlSapphireToken);

    /** The computer's colour when the player takes this one: pearl, or gold if the player took pearl. */
    fun opponent(): SlTokenColor = if (this == PEARL) GOLD else PEARL
}

/** A fresh game with whoever [firstTurn] names to roll first. */
fun snakesAndLaddersGameFor(firstTurn: FirstTurn): SnakesAndLaddersState =
    SnakesAndLaddersState(turn = if (firstTurn == FirstTurn.YOU) SL_HUMAN else SL_COMPUTER)

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
    /** The token colour the player chose; the computer's is [SlTokenColor.opponent]. */
    val humanColor: SlTokenColor = SlTokenColor.GOLD,
    /** Who the player chose to roll first. */
    val firstTurn: FirstTurn = FirstTurn.YOU,
    val game: SnakesAndLaddersState = snakesAndLaddersGameFor(firstTurn),
    /** False while the player is still choosing: nobody rolls yet. */
    val started: Boolean = false,
    /** The roll just played, for the die, the board's animation and the note under the turn line. */
    val lastMove: SnakesAndLaddersMove? = null,
    /** Set once the engine has confirmed a win. */
    val winner: Int? = null,
    /** A roll is being thrown, played out on the board or confirmed. */
    val isBusy: Boolean = false,
    val wonCouponId: String? = null
) {
    val computerColor: SlTokenColor get() = humanColor.opponent()

    /** Each player's pawn colours, by player index. */
    val tokenColors: List<List<Color>> get() = listOf(humanColor.colors, computerColor.colors)

    val isHumanTurn: Boolean get() = game.turn == SL_HUMAN
    val canRoll: Boolean get() = started && winner == null && !isBusy && isHumanTurn
}
