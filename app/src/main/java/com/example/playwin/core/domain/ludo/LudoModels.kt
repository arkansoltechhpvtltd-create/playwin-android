package com.example.playwin.core.domain.ludo

enum class LudoColor { RED, GREEN, YELLOW, BLUE }

/**
 * @param position -1 = in base; 0..50 = on the shared 52-cell path (as a
 * local offset from this color's own entry square); 51..56 = this color's
 * private home column, where 56 is the finish/home cell.
 */
data class Token(val color: LudoColor, val index: Int, val position: Int)

/** A board cell tagged by kind, so a home-column cell can never collide with a shared-track cell. */
sealed interface BoardCell {
    data class Shared(val index: Int) : BoardCell
    data class HomeColumn(val color: LudoColor, val index: Int) : BoardCell
}

data class LudoState(
    val tokens: List<Token>,
    val currentPlayerIndex: Int,
    /** Colors in play, in turn order; [currentPlayerIndex] indexes into this. A 1v1 game uses [LudoEngine.TWO_PLAYER]. */
    val players: List<LudoColor> = LudoEngine.PLAYER_ORDER,
    val diceValue: Int? = null,
    val hasRolled: Boolean = false
)

val LudoState.currentColor: LudoColor get() = players[currentPlayerIndex]
