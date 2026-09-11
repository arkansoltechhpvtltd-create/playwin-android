package com.example.playwin.core.domain.repository

import com.example.playwin.core.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

/**
 * The single authority on who is signed in.
 *
 * Today [com.example.playwin.core.data.mock.MockAuthRepository] keeps accounts
 * in memory; a real build swaps it for a network-backed implementation that
 * talks to a server-authoritative auth service, with no changes to any
 * ViewModel or screen.
 */
interface AuthRepository {
    /** Emits the signed-in user, or null when logged out. Drives app-level navigation. */
    fun observeCurrentUser(): Flow<AuthUser?>

    suspend fun login(identifier: String, password: String): Result<AuthUser>

    suspend fun signUp(
        displayName: String,
        identifier: String,
        password: String,
        isAdult: Boolean
    ): Result<AuthUser>

    /** Signs in an ephemeral guest session - no credentials, nothing persisted beyond the session. */
    suspend fun loginAsGuest(): Result<AuthUser>

    suspend fun updateDisplayName(displayName: String): Result<AuthUser>

    suspend fun logout()
}
