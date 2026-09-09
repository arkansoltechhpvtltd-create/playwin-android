package com.example.playwin.feature.game.eightball

const val EIGHT_BALL_TURN_SECONDS = 20

data class EightBallUiState(
    val balls: List<Ball> = EightBallTable.initialRack(),
    val currentPlayer: Int = 1,
    val playerGroups: Map<Int, BallGroup> = emptyMap(),
    val secondsLeft: Int = EIGHT_BALL_TURN_SECONDS,
    val isBallsMoving: Boolean = false,
    val isResolving: Boolean = false,
    val winner: Int? = null,
    val fouled: Boolean = false,
    val lastTurnTimedOut: Boolean = false,
    val wonCouponId: String? = null
) {
    val canAim: Boolean get() = winner == null && !isBallsMoving && !isResolving
}
