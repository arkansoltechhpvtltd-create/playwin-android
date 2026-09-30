package com.example.playwin.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.model.AppSettings
import com.example.playwin.core.domain.model.AuthUser
import com.example.playwin.core.domain.repository.AuthRepository
import com.example.playwin.core.domain.repository.GamesRepository
import com.example.playwin.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Exposes the two pieces of state the app root needs before it can decide what to show: who's signed in, and the theme. */
@HiltViewModel
class SessionViewModel @Inject constructor(
    authRepository: AuthRepository,
    settingsRepository: SettingsRepository,
    gamesRepository: GamesRepository
) : ViewModel() {

    val authUser: StateFlow<AuthUser?> = authRepository.observeCurrentUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val settings: StateFlow<AppSettings> = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    init {
        // Start loading the games catalogue while the login screen is still up,
        // so the lobby has it the moment someone signs in or continues as guest
        // (a guest's login makes no network call of its own to hide the wait).
        viewModelScope.launch { gamesRepository.observeGames().first() }
    }
}
