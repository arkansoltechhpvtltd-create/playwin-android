package com.example.playwin.feature.game.carrom

import com.example.playwin.core.domain.carrom.CarromBoard
import com.example.playwin.core.domain.carrom.CarromFoul
import com.example.playwin.core.domain.carrom.CarromMatch
import com.example.playwin.core.domain.carrom.CarromRules
import com.example.playwin.feature.game.FirstTurn
import com.example.playwin.core.domain.carrom.Coin
import com.example.playwin.core.domain.carrom.CoinColor
import com.example.playwin.core.domain.carrom.DueEvent
import com.example.playwin.core.domain.carrom.QueenEvent
import com.example.playwin.core.domain.carrom.STRIKER_ID
import com.example.playwin.core.domain.carrom.Vec2

const val CARROM_TURN_SECONDS = 20

/** How far the striker may be slid either side of its baseline's centre before a shot ([CarromBoard.STRIKER_SLIDE_HALF_SPAN]). */
const val STRIKER_SLIDE_HALF_SPAN = CarromBoard.STRIKER_SLIDE_HALF_SPAN

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

/** In "Play vs Computer" the human is player 1 (bottom baseline); the computer is player 2 (top). */
const val CARROM_HUMAN = 1
const val CARROM_COMPUTER = 2

/**
 * A fresh vs-Computer match: the player owns [humanColor] from the start (no
 * first-pot draw) and the computer the other, and whoever [firstTurn] names
 * breaks, the striker on their baseline.
 */
fun carromMatchFor(humanColor: CoinColor, firstTurn: FirstTurn): CarromMatch {
    val breaker = if (firstTurn == FirstTurn.YOU) CARROM_HUMAN else CARROM_COMPUTER
    val setup = CarromBoard.initialSetup()
    return CarromMatch(
        coins = if (breaker == CARROM_HUMAN) setup else CarromRules.respotStriker(setup, breaker),
        currentPlayer = breaker,
        playerColors = mapOf(CARROM_HUMAN to humanColor, CARROM_COMPUTER to humanColor.other())
    )
}

data class CarromUiState(
    /** The coins the player chose; the computer plays the other colour. */
    val humanColor: CoinColor = CoinColor.WHITE,
    /** Who the player chose to break. */
    val firstTurn: FirstTurn = FirstTurn.YOU,
    val match: CarromMatch = carromMatchFor(humanColor, firstTurn),
    /** False while the player is still choosing: nothing moves and no clock runs. */
    val started: Boolean = false,
    /** What's drawn: the match's coins, or the pieces mid-flight while a shot plays out. */
    val coins: List<Coin> = match.coins,
    val secondsLeft: Int = CARROM_TURN_SECONDS,
    val isPiecesMoving: Boolean = false,
    val isResolving: Boolean = false,
    val isComputerThinking: Boolean = false,
    val winner: Int? = null,
    val isDraw: Boolean = false,
    /** The last shot was a foul. Kept until the next shot has played out, so it can be read. */
    val fouled: Boolean = false,
    /** Who took the last shot, so its foul or due note can say whose it was. */
    val lastShooter: Int? = null,
    /** What the last shot's foul was, if it was one. */
    val lastFoul: CarromFoul? = null,
    /** How a due was settled on the last shot, if one was. */
    val lastDue: DueEvent? = null,
    val lastTurnTimedOut: Boolean = false,
    /** What happened to the queen on the last shot (claimed, returned...), for the turn banner. */
    val lastQueenEvent: QueenEvent? = null,
    val wonCouponId: String? = null
) {
    val currentPlayer: Int get() = match.currentPlayer
    /** Who must cover the queen with their next shot, if anyone. */
    val queenCoverOwedBy: Int? get() = match.queenCoverOwedBy
    val playerColors: Map<Int, CoinColor> get() = match.playerColors
    val scores: Map<Int, Int> get() = match.scores
    val isGameOver: Boolean get() = winner != null || isDraw
    val canAim: Boolean
        get() = started && !isGameOver && !isPiecesMoving && !isResolving && !isComputerThinking && currentPlayer == CARROM_HUMAN
}
