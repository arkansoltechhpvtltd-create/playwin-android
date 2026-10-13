package com.example.playwin.feature.game.eightball

import com.example.playwin.core.domain.eightball.Ball
import com.example.playwin.core.domain.eightball.BallGroup
import com.example.playwin.core.domain.eightball.EightBallFoul
import com.example.playwin.core.domain.eightball.EightBallMatch

const val EIGHT_BALL_TURN_SECONDS = 20

/** In "Play vs Computer" the human is player 1 and breaks; the computer is player 2. */
const val EIGHT_BALL_HUMAN = 1
const val EIGHT_BALL_COMPUTER = 2

data class EightBallUiState(
    val match: EightBallMatch = EightBallMatch(),
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
    val lastTurnTimedOut: Boolean = false,
    val wonCouponId: String? = null
) {
    val currentPlayer: Int get() = match.currentPlayer
    val playerGroups: Map<Int, BallGroup> get() = match.playerGroups
    val canAim: Boolean
        get() = winner == null && !isBallsMoving && !isResolving && !isComputerThinking && currentPlayer == EIGHT_BALL_HUMAN
}
