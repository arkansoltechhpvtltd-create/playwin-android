package com.example.playwin.feature.lobby

import com.example.playwin.core.domain.model.Game

data class LobbyUiState(
    val games: List<Game> = emptyList(),
    val couponBalance: Int = 0,
    val isLoading: Boolean = true
)
