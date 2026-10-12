package com.example.playwin.core.data.remote

import com.example.playwin.core.domain.model.AuthUser
import com.example.playwin.core.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown for expected auth failures so screens can show their message. */
class AuthException(message: String) : Exception(message)

private const val DISPLAY_NAME_KEY = "display_name"
private const val IS_ADULT_KEY = "is_adult"
private const val NETWORK_ERROR_MESSAGE = "Couldn't reach the server - check your internet connection and try again"

/**
 * Real, network-backed [AuthRepository] implementation, calling Supabase Auth
 * (email + password) for login/signup/session/display-name. Guest sessions are
 * intentionally local-only - [loginAsGuest] never touches Supabase - and take
 * priority over any real Supabase session in [observeCurrentUser] while active.
 */
@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) : AuthRepository {

    private val auth get() = supabaseClient.auth

    private val guestUser = MutableStateFlow<AuthUser?>(null)

    override fun observeCurrentUser(): Flow<AuthUser?> =
        combine(guestUser, auth.sessionStatus) { guest, status ->
            guest ?: (status as? SessionStatus.Authenticated)?.session?.user?.let(::mapToAuthUser)
        }

    override suspend fun login(identifier: String, password: String): Result<AuthUser> {
        guestUser.value = null
        return try {
            auth.signInWith(Email) {
                email = identifier
                this.password = password
            }
            val user = auth.currentUserOrNull()
                ?: return Result.failure(AuthException("Login failed - no session returned"))
            Result.success(mapToAuthUser(user))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(AuthException(friendlyMessage(e, "Login failed")))
        }
    }

    override suspend fun signUp(
        displayName: String,
        identifier: String,
        password: String,
        isAdult: Boolean
    ): Result<AuthUser> {
        guestUser.value = null
        return try {
            auth.signUpWith(Email) {
                email = identifier
                this.password = password
                data = buildJsonObject {
                    put(DISPLAY_NAME_KEY, displayName)
                    put(IS_ADULT_KEY, isAdult)
                }
            }
            val user = auth.currentUserOrNull()
                ?: return Result.failure(
                    AuthException("Account created - check your email to confirm it, then log in")
                )
            Result.success(mapToAuthUser(user))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(AuthException(friendlyMessage(e, "Sign up failed")))
        }
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
        guestUser.value = guest
        return Result.success(guest)
    }

    override suspend fun updateDisplayName(displayName: String): Result<AuthUser> {
        guestUser.value?.let { guest ->
            val updated = guest.copy(displayName = displayName, initials = initialsFrom(displayName))
            guestUser.value = updated
            return Result.success(updated)
        }
        return try {
            val updated = auth.updateUser {
                data { put(DISPLAY_NAME_KEY, displayName) }
            }
            Result.success(mapToAuthUser(updated))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(AuthException(friendlyMessage(e, "Not logged in")))
        }
    }

    /**
     * What to show for a failed auth call. [AuthRestException.message] dumps the
     * full request (URL, headers) for debugging, and a network failure's message
     * is just as raw, so only the server's own [AuthRestException.errorDescription]
     * is ever shown as-is.
     */
    private fun friendlyMessage(e: Exception, fallback: String): String = when (e) {
        is AuthRestException -> e.errorDescription
        is RestException -> fallback
        else -> NETWORK_ERROR_MESSAGE
    }

    override suspend fun logout() {
        if (guestUser.value != null) {
            guestUser.value = null
            return
        }
        auth.signOut()
    }

    private fun mapToAuthUser(userInfo: UserInfo): AuthUser {
        val metadata = userInfo.userMetadata
        val identifier = userInfo.email ?: userInfo.phone.orEmpty()
        val displayName = metadata?.get(DISPLAY_NAME_KEY)?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: identifier.ifBlank { "Player" }
        val isAdult = metadata?.get(IS_ADULT_KEY)?.jsonPrimitive?.contentOrNull
            ?.toBooleanStrictOrNull() ?: true
        return AuthUser(
            id = userInfo.id,
            displayName = displayName,
            initials = initialsFrom(displayName),
            identifier = identifier,
            isAdult = isAdult,
            isGuest = false
        )
    }

    private fun initialsFrom(name: String): String =
        name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifEmpty { "?" }
}
