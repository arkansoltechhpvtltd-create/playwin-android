package com.example.playwin.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.repository.AuthRepository
import com.example.playwin.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class NameEditState(
    val isEditing: Boolean = false,
    val editedName: String = "",
    val error: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val nameEditState = MutableStateFlow(NameEditState())

    val uiState: StateFlow<ProfileUiState> = combine(
        authRepository.observeCurrentUser(),
        settingsRepository.observeSettings(),
        nameEditState
    ) { user, settings, editState ->
        ProfileUiState(
            displayName = user?.displayName.orEmpty(),
            initials = user?.initials.orEmpty(),
            identifier = user?.identifier.orEmpty(),
            isGuest = user?.isGuest == true,
            isEditingName = editState.isEditing,
            editedName = editState.editedName,
            nameError = editState.error,
            darkModeEnabled = settings.darkModeEnabled,
            notificationsEnabled = settings.notificationsEnabled,
            soundEnabled = settings.soundEnabled,
            dailyLimitMinutes = settings.dailyLimitMinutes,
            selfExclusionEnabled = settings.selfExclusionEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState()
    )

    fun onEditNameClick() {
        nameEditState.value = NameEditState(isEditing = true, editedName = uiState.value.displayName)
    }

    fun onEditedNameChange(value: String) {
        nameEditState.update { it.copy(editedName = value, error = null) }
    }

    fun onCancelEditName() {
        nameEditState.value = NameEditState()
    }

    fun onSaveNameClick() {
        val name = nameEditState.value.editedName.trim()
        if (name.isEmpty()) {
            nameEditState.update { it.copy(error = "Name can't be empty") }
            return
        }
        viewModelScope.launch {
            authRepository.updateDisplayName(name)
            nameEditState.value = NameEditState()
        }
    }

    fun onDarkModeChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkModeEnabled(enabled) }
    }

    fun onNotificationsChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
    }

    fun onSoundChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundEnabled(enabled) }
    }

    fun onDailyLimitChange(minutes: Int) {
        viewModelScope.launch { settingsRepository.setDailyLimitMinutes(minutes) }
    }

    fun onSelfExclusionChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSelfExclusionEnabled(enabled) }
    }

    fun onLogoutClick() {
        viewModelScope.launch { authRepository.logout() }
    }

    /** A guest has no account to sign back into - end the guest session so the auth gate shows signup/login. */
    fun onCreateAccountClick() {
        viewModelScope.launch { authRepository.logout() }
    }
}
