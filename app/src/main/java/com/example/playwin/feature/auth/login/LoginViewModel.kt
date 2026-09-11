package com.example.playwin.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.repository.AuthRepository
import com.example.playwin.feature.auth.AuthValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onIdentifierChange(value: String) {
        _uiState.update { it.copy(identifier = value, identifierError = null, generalError = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, generalError = null) }
    }

    fun onLoginClick() {
        val state = _uiState.value
        val identifierError = AuthValidation.identifierError(state.identifier)
        val passwordError = if (state.password.isEmpty()) "Enter your password" else null

        if (identifierError != null || passwordError != null) {
            _uiState.update { it.copy(identifierError = identifierError, passwordError = passwordError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, generalError = null) }
            val result = authRepository.login(state.identifier.trim(), state.password)
            result.onFailure { error ->
                _uiState.update { it.copy(isSubmitting = false, generalError = error.message) }
            }
            result.onSuccess {
                // Success flips the shared auth state; the app root swaps to the lobby.
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun onContinueAsGuestClick() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, generalError = null) }
            val result = authRepository.loginAsGuest()
            result.onFailure { error ->
                _uiState.update { it.copy(isSubmitting = false, generalError = error.message) }
            }
            result.onSuccess {
                // Success flips the shared auth state; the app root swaps to the lobby.
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }
}
