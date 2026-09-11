package com.example.playwin.feature.auth.signup

data class SignupUiState(
    val displayName: String = "",
    val identifier: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isAdult: Boolean = false,
    val displayNameError: String? = null,
    val identifierError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val ageError: String? = null,
    val generalError: String? = null,
    val isSubmitting: Boolean = false
)
