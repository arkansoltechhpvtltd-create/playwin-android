package com.example.playwin.feature.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.core.domain.repository.GamesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LobbyViewModel @Inject constructor(
    gamesRepository: GamesRepository,
    couponsRepository: CouponsRepository
) : ViewModel() {

    val uiState: StateFlow<LobbyUiState> = combine(
        gamesRepository.observeGames(),
        couponsRepository.observeCouponBalance()
    ) { games, balance ->
        LobbyUiState(games = games, couponBalance = balance, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LobbyUiState()
    )
}
