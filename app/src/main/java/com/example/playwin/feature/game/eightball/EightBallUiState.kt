package com.example.playwin.feature.game.eightball

import com.example.playwin.core.domain.eightball.Ball
import com.example.playwin.core.domain.eightball.BallGroup
import com.example.playwin.core.domain.eightball.EightBallFoul
import com.example.playwin.core.domain.eightball.EightBallMatch
import com.example.playwin.feature.game.FirstTurn

const val EIGHT_BALL_TURN_SECONDS = 20

/** In "Play vs Computer" the human is player 1 and the computer player 2; either may break. */
const val EIGHT_BALL_HUMAN = 1
const val EIGHT_BALL_COMPUTER = 2

/** A fresh rack with whoever [firstTurn] names to break. Groups stay open until the first legal pot, as always. */
fun eightBallMatchFor(firstTurn: FirstTurn): EightBallMatch =
    EightBallMatch(currentPlayer = if (firstTurn == FirstTurn.YOU) EIGHT_BALL_HUMAN else EIGHT_BALL_COMPUTER)

data class EightBallUiState(
    /** Who the player chose to break. */
    val firstTurn: FirstTurn = FirstTurn.YOU,
    val match: EightBallMatch = eightBallMatchFor(firstTurn),
    /** False while the player is still choosing: nothing moves and no clock runs. */
    val started: Boolean = false,
    /** What's drawn: the match's balls, or the balls mid-roll while a shot plays out. */
    val balls: List<Ball> = match.balls,
    val secondsLeft: Int = EIGHT_BALL_TURN_SECONDS,
    val isBallsMoving: Boolean = false,
    val isResolving: Boolean = false,
    val isComputerThinking: Boolean = false,
    val winner: Int? = null,
    /** The game ended on a foul (the 8 sunk early, or with a scratch). */
    val endedOnFoul: Boolean = false,
    /** The previous shot's foul, if it was one, for the turn banner. */
    val lastFoul: EightBallFoul? = null,
    /** Who committed [lastFoul]. */
    val lastFoulBy: Int? = null,
    val lastTurnTimedOut: Boolean = false,
    val wonCouponId: String? = null
) {
    val currentPlayer: Int get() = match.currentPlayer
    val playerGroups: Map<Int, BallGroup> get() = match.playerGroups
    val canAim: Boolean
        get() = started && winner == null && !isBallsMoving && !isResolving && !isComputerThinking && currentPlayer == EIGHT_BALL_HUMAN
}
