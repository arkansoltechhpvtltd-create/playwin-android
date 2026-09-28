package com.example.playwin.feature.game.carrom

import com.example.playwin.core.domain.carrom.CarromMatch
import com.example.playwin.core.domain.carrom.Coin
import com.example.playwin.core.domain.carrom.CoinColor

const val CARROM_TURN_SECONDS = 20

/** In "Play vs Computer" the human is player 1 (bottom baseline) and shoots first; the computer is player 2. */
const val CARROM_HUMAN = 1
const val CARROM_COMPUTER = 2

data class CarromUiState(
    val match: CarromMatch = CarromMatch(),
    /** What's drawn: the match's coins, or the pieces mid-flight while a shot plays out. */
    val coins: List<Coin> = match.coins,
    val secondsLeft: Int = CARROM_TURN_SECONDS,
    val isPiecesMoving: Boolean = false,
    val isResolving: Boolean = false,
    val isComputerThinking: Boolean = false,
    val winner: Int? = null,
    val isDraw: Boolean = false,
    val fouled: Boolean = false,
    val lastTurnTimedOut: Boolean = false,
    val wonCouponId: String? = null
) {
    val currentPlayer: Int get() = match.currentPlayer
    val playerColors: Map<Int, CoinColor> get() = match.playerColors
    val scores: Map<Int, Int> get() = match.scores
    val isGameOver: Boolean get() = winner != null || isDraw
    val canAim: Boolean
        get() = !isGameOver && !isPiecesMoving && !isResolving && !isComputerThinking && currentPlayer == CARROM_HUMAN
}
