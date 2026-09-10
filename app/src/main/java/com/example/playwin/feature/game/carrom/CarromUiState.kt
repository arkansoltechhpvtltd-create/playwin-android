package com.example.playwin.feature.game.carrom

const val CARROM_TURN_SECONDS = 20

data class CarromUiState(
    val coins: List<Coin> = CarromBoard.initialSetup(),
    val currentPlayer: Int = 1,
    val playerColors: Map<Int, CoinColor> = emptyMap(),
    val scores: Map<Int, Int> = mapOf(1 to 0, 2 to 0),
    val secondsLeft: Int = CARROM_TURN_SECONDS,
    val isPiecesMoving: Boolean = false,
    val isResolving: Boolean = false,
    val winner: Int? = null,
    val isDraw: Boolean = false,
    val fouled: Boolean = false,
    val lastTurnTimedOut: Boolean = false,
    val wonCouponId: String? = null
) {
    val canAim: Boolean get() = winner == null && !isDraw && !isPiecesMoving && !isResolving
}
