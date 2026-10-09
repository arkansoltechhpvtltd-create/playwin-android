package com.example.playwin.feature.game.carrom

import com.example.playwin.core.domain.carrom.CarromBoard
import com.example.playwin.core.domain.carrom.CarromMatch
import com.example.playwin.core.domain.carrom.Coin
import com.example.playwin.core.domain.carrom.CoinColor
import com.example.playwin.core.domain.carrom.STRIKER_ID
import com.example.playwin.core.domain.carrom.Vec2

const val CARROM_TURN_SECONDS = 20

/**
 * How far the striker may be slid either side of its baseline's centre before
 * a shot, in board units. carrom_board.jpg paints each baseline's end circles
 * 290 units either side of the centre; this keeps a striker radius inside them.
 */
const val STRIKER_SLIDE_HALF_SPAN = 269f

/**
 * [coins] with the striker slid sideways along its baseline to [x], clamped to
 * [STRIKER_SLIDE_HALF_SPAN] either side of the centre; its distance from the
 * frame never changes. Null if there's no striker on the board or it would
 * overlap a coin there.
 */
fun slideStriker(coins: List<Coin>, x: Float): List<Coin>? {
    val striker = coins.firstOrNull { it.id == STRIKER_ID && !it.pocketed } ?: return null
    val center = CarromBoard.SIZE / 2f
    val target = Vec2(x.coerceIn(center - STRIKER_SLIDE_HALF_SPAN, center + STRIKER_SLIDE_HALF_SPAN), striker.position.y)
    val clearance = CarromBoard.STRIKER_RADIUS + CarromBoard.COIN_RADIUS
    if (coins.any { it.id != STRIKER_ID && !it.pocketed && (it.position - target).length < clearance }) return null
    return coins.map { if (it.id == STRIKER_ID) it.copy(position = target) else it }
}

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
