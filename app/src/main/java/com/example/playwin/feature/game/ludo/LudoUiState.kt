package com.example.playwin.feature.game.ludo

import com.example.playwin.core.domain.ludo.LudoColor
import com.example.playwin.core.domain.ludo.LudoEngine
import com.example.playwin.core.domain.ludo.LudoState
import com.example.playwin.core.domain.ludo.Token
import com.example.playwin.core.domain.ludo.currentColor

const val LUDO_TURN_SECONDS = 20

/** In "Play vs Computer" the human is Red unless they choose otherwise, and always rolls first. */
val LUDO_DEFAULT_HUMAN = LudoColor.RED

/** The colour across the board from [this]: the computer's, when the player takes [this]. */
fun LudoColor.across(): LudoColor = when (this) {
    LudoColor.RED -> LudoColor.YELLOW
    LudoColor.YELLOW -> LudoColor.RED
    LudoColor.GREEN -> LudoColor.BLUE
    LudoColor.BLUE -> LudoColor.GREEN
}

/** A 1v1 game with the player on [human] and the computer in the opposite corner; the player rolls first. */
fun ludoGameFor(human: LudoColor): LudoState = LudoEngine.initialState(listOf(human, human.across()))

data class LudoUiState(
    /** The colour the player chose; the computer plays the one across the board. */
    val humanColor: LudoColor = LUDO_DEFAULT_HUMAN,
    val game: LudoState = ludoGameFor(humanColor),
    /** False while the player is still choosing their colour: nothing moves and no clock runs. */
    val started: Boolean = false,
    val movableTokens: List<Token> = emptyList(),
    val secondsLeft: Int = LUDO_TURN_SECONDS,
    val winner: LudoColor? = null,
    val isResolving: Boolean = false,
    val lastTurnTimedOut: Boolean = false,
    /** The last turn ended on a third 6 in a row, which is void. */
    val lastTurnForfeited: Boolean = false,
    val wonCouponId: String? = null
) {
    val computerColor: LudoColor get() = humanColor.across()
    val currentColor: LudoColor get() = game.currentColor
    val isHumanTurn: Boolean get() = currentColor == humanColor
    val canRoll: Boolean get() = started && winner == null && !isResolving && isHumanTurn && game.diceValue == null
}
