package com.example.playwin.feature.auth.signup

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
class SignupViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    fun onDisplayNameChange(value: String) {
        _uiState.update { it.copy(displayName = value, displayNameError = null, generalError = null) }
    }

    fun onIdentifierChange(value: String) {
        _uiState.update { it.copy(identifier = value, identifierError = null, generalError = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, confirmPasswordError = null, generalError = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, confirmPasswordError = null, generalError = null) }
    }

    fun onIsAdultChange(value: Boolean) {
        _uiState.update { it.copy(isAdult = value, ageError = null, generalError = null) }
    }

    fun onCreateAccountClick() {
        val state = _uiState.value
        val displayNameError = AuthValidation.displayNameError(state.displayName)
        val identifierError = AuthValidation.identifierError(state.identifier)
        val passwordError = AuthValidation.passwordError(state.password)
        val confirmPasswordError = AuthValidation.confirmPasswordError(state.password, state.confirmPassword)
        val ageError = if (!state.isAdult) "You must be 18 or older to play" else null

        if (listOfNotNull(displayNameError, identifierError, passwordError, confirmPasswordError, ageError).isNotEmpty()) {
            _uiState.update {
                it.copy(
                    displayNameError = displayNameError,
                    identifierError = identifierError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmPasswordError,
                    ageError = ageError
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, generalError = null) }
            val result = authRepository.signUp(
                displayName = state.displayName.trim(),
                identifier = state.identifier.trim(),
                password = state.password,
                isAdult = state.isAdult
            )
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
