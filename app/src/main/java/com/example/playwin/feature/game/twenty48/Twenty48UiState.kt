package com.example.playwin.feature.game.twenty48

import com.example.playwin.core.domain.twenty48.Twenty48Rules

const val GRID_SIZE = Twenty48Rules.GRID_SIZE
const val WIN_TILE = Twenty48Rules.WIN_TILE

typealias SwipeDirection = com.example.playwin.core.domain.twenty48.SwipeDirection

data class Twenty48UiState(
    val board: List<List<Int>> = List(GRID_SIZE) { List(GRID_SIZE) { 0 } },
    val elapsedSeconds: Int = 0,
    val hasWon: Boolean = false,
    val isGameOver: Boolean = false,
    val isResolving: Boolean = false,
    val wonCouponId: String? = null
)
