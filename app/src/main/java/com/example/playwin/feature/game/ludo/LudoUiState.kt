package com.example.playwin.feature.game.ludo

import com.example.playwin.core.domain.ludo.LudoColor
import com.example.playwin.core.domain.ludo.LudoEngine
import com.example.playwin.core.domain.ludo.LudoState
import com.example.playwin.core.domain.ludo.Token

const val LUDO_TURN_SECONDS = 20

data class LudoUiState(
    val game: LudoState = LudoEngine.initialState(),
    val movableTokens: List<Token> = emptyList(),
    val secondsLeft: Int = LUDO_TURN_SECONDS,
    val winner: LudoColor? = null,
    val isResolving: Boolean = false,
    val lastTurnTimedOut: Boolean = false,
    val wonCouponId: String? = null
) {
    val currentColor: LudoColor get() = LudoEngine.PLAYER_ORDER[game.currentPlayerIndex]
    val canRoll: Boolean get() = winner == null && !isResolving && game.diceValue == null
}
