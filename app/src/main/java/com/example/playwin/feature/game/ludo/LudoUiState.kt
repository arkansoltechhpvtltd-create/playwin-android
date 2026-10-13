package com.example.playwin.feature.game.ludo

import com.example.playwin.core.domain.ludo.LudoColor
import com.example.playwin.core.domain.ludo.LudoEngine
import com.example.playwin.core.domain.ludo.LudoState
import com.example.playwin.core.domain.ludo.Token
import com.example.playwin.core.domain.ludo.currentColor

const val LUDO_TURN_SECONDS = 20

/** In "Play vs Computer" the human is Red and moves first; the computer is Yellow. */
val LUDO_HUMAN = LudoColor.RED
val LUDO_COMPUTER = LudoColor.YELLOW

data class LudoUiState(
    val game: LudoState = LudoEngine.initialState(LudoEngine.TWO_PLAYER),
    val movableTokens: List<Token> = emptyList(),
    val secondsLeft: Int = LUDO_TURN_SECONDS,
    val winner: LudoColor? = null,
    val isResolving: Boolean = false,
    val lastTurnTimedOut: Boolean = false,
    /** The last turn ended on a third 6 in a row, which is void. */
    val lastTurnForfeited: Boolean = false,
    val wonCouponId: String? = null
) {
    val currentColor: LudoColor get() = game.currentColor
    val isHumanTurn: Boolean get() = currentColor == LUDO_HUMAN
    val canRoll: Boolean get() = winner == null && !isResolving && isHumanTurn && game.diceValue == null
}
