package com.example.playwin.feature.game.connectfour

const val CONNECT4_ROWS = 6
const val CONNECT4_COLS = 7
const val CONNECT4_TURN_SECONDS = 20

data class Connect4UiState(
    val board: List<List<Int>> = List(CONNECT4_ROWS) { List(CONNECT4_COLS) { 0 } },
    val currentPlayer: Int = 1,
    val winner: Int? = null,
    val isDraw: Boolean = false,
    val secondsLeft: Int = CONNECT4_TURN_SECONDS,
    val isResolving: Boolean = false,
    val lastTurnTimedOut: Boolean = false,
    val wonCouponId: String? = null
)
