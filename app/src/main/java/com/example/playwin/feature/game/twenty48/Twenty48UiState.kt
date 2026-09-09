package com.example.playwin.feature.game.twenty48

const val GRID_SIZE = 4
const val WIN_TILE = 2048

enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }

data class Twenty48UiState(
    val board: List<List<Int>> = List(GRID_SIZE) { List(GRID_SIZE) { 0 } },
    val elapsedSeconds: Int = 0,
    val hasWon: Boolean = false,
    val isGameOver: Boolean = false,
    val isResolving: Boolean = false,
    val wonCouponId: String? = null
)
