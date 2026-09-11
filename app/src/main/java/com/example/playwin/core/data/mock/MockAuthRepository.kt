package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.AuthUser
import com.example.playwin.core.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown for expected auth failures (bad credentials, duplicate account) so screens can show their message. */
class AuthException(message: String) : Exception(message)

/**
 * In-memory account store and session holder, pre-seeded with a demo account
 * so the login screen is usable without signing up first (demo@playwin.app / password123).
 */
@Singleton
class MockAuthRepository @Inject constructor() : AuthRepository {

    private data class Account(val user: AuthUser, val password: String)

    private val accountsByIdentifier = mutableMapOf<String, Account>()
    private val currentUser = MutableStateFlow<AuthUser?>(null)

    init {
        val demoUser = AuthUser(
            id = "demo",
            displayName = "Arka Sol",
            initials = "AS",
            identifier = "demo@playwin.app",
            isAdult = true
        )
        accountsByIdentifier[normalize(demoUser.identifier)] = Account(demoUser, "password123")
    }

    override fun observeCurrentUser(): Flow<AuthUser?> = currentUser.asStateFlow()

    override suspend fun login(identifier: String, password: String): Result<AuthUser> {
        val account = accountsByIdentifier[normalize(identifier)]
            ?: return Result.failure(AuthException("No account found for that email or phone number"))
        if (account.password != password) {
            return Result.failure(AuthException("Incorrect password"))
        }
        currentUser.value = account.user
        return Result.success(account.user)
    }

    override suspend fun signUp(
        displayName: String,
        identifier: String,
        password: String,
        isAdult: Boolean
    ): Result<AuthUser> {
        val key = normalize(identifier)
        if (accountsByIdentifier.containsKey(key)) {
            return Result.failure(AuthException("An account with that email or phone number already exists"))
        }
        val user = AuthUser(
            id = "user-${System.currentTimeMillis()}",
            displayName = displayName,
            initials = initialsFrom(displayName),
            identifier = identifier,
            isAdult = isAdult
        )
        accountsByIdentifier[key] = Account(user, password)
        currentUser.value = user
        return Result.success(user)
    }

    override suspend fun loginAsGuest(): Result<AuthUser> {
        val guest = AuthUser(
            id = "guest-${System.currentTimeMillis()}",
            displayName = "Guest",
            initials = "G",
            identifier = "",
            isAdult = true,
            isGuest = true
        )
        currentUser.value = guest
        return Result.success(guest)
    }

    override suspend fun updateDisplayName(displayName: String): Result<AuthUser> {
        val user = currentUser.value
            ?: return Result.failure(AuthException("Not logged in"))
        val updated = user.copy(displayName = displayName, initials = initialsFrom(displayName))
        accountsByIdentifier[normalize(user.identifier)]?.let { account ->
            accountsByIdentifier[normalize(user.identifier)] = account.copy(user = updated)
        }
        currentUser.value = updated
        return Result.success(updated)
    }

    override suspend fun logout() {
        currentUser.value = null
    }

    private fun normalize(identifier: String) = identifier.trim().lowercase()

    private fun initialsFrom(name: String): String =
        name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifEmpty { "?" }
}
