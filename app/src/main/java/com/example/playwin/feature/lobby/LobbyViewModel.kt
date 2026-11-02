package com.example.playwin.feature.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.model.Game
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.core.domain.repository.GamesRepository
import com.example.playwin.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LobbyViewModel @Inject constructor(
    gamesRepository: GamesRepository,
    couponsRepository: CouponsRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    // Each source starts with a placeholder so neither holds the other up: the
    // balance shows as soon as it's known, and the grid stays in its loading
    // state only until the games themselves arrive.
    val uiState: StateFlow<LobbyUiState> = combine(
        gamesRepository.observeGames().map<List<Game>, List<Game>?> { it }.onStart { emit(null) },
        couponsRepository.observeCouponBalance().onStart { emit(0) },
        settingsRepository.observeSettings().map { it.selfExclusionEnabled }.onStart { emit(false) }
    ) { games, balance, selfExcluded ->
        LobbyUiState(
            games = games.orEmpty(),
            couponBalance = balance,
            isLoading = games == null,
            isSelfExcluded = selfExcluded
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LobbyUiState()
    )
}
