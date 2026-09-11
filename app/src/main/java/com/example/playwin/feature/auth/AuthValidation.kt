package com.example.playwin.feature.auth

private val EMAIL_REGEX = Regex("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$")
private val PHONE_REGEX = Regex("^\\+?[0-9]{7,15}$")

/** Shared field validation for the login and signup forms. Returns an error message, or null if valid. */
object AuthValidation {

    fun identifierError(identifier: String): String? {
        val trimmed = identifier.trim()
        return when {
            trimmed.isEmpty() -> "Enter your email or phone number"
            EMAIL_REGEX.matches(trimmed) || PHONE_REGEX.matches(trimmed) -> null
            else -> "Enter a valid email or phone number"
        }
    }

    fun passwordError(password: String): String? = when {
        password.isEmpty() -> "Enter your password"
        password.length < 6 -> "Password must be at least 6 characters"
        else -> null
    }

    fun confirmPasswordError(password: String, confirmPassword: String): String? = when {
        confirmPassword.isEmpty() -> "Confirm your password"
        confirmPassword != password -> "Passwords don't match"
        else -> null
    }

    fun displayNameError(displayName: String): String? =
        if (displayName.trim().isEmpty()) "Enter your name" else null
}
