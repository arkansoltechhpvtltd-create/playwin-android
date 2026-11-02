package com.example.playwin.core.data.remote

import android.util.Log
import com.example.playwin.core.data.mock.MockSettingsRepository
import com.example.playwin.core.domain.model.AppSettings
import com.example.playwin.core.domain.model.AuthUser
import com.example.playwin.core.domain.repository.AuthRepository
import com.example.playwin.core.domain.repository.SettingsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SupabaseSettings"
private const val TABLE = "profiles"

@Serializable
private data class ProfileInsertRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("dark_mode") val darkMode: Boolean,
    val notifications: Boolean,
    val sound: Boolean,
    @SerialName("daily_limit_minutes") val dailyLimitMinutes: Int,
    @SerialName("self_excluded") val selfExcluded: Boolean,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class DarkModeUpdate(
    @SerialName("dark_mode") val darkMode: Boolean,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class NotificationsUpdate(
    val notifications: Boolean,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class SoundUpdate(
    val sound: Boolean,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class DailyLimitUpdate(
    @SerialName("daily_limit_minutes") val dailyLimitMinutes: Int,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class SelfExclusionUpdate(
    @SerialName("self_excluded") val selfExcluded: Boolean,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class ProfileRow(
    @SerialName("dark_mode") val darkMode: Boolean,
    val notifications: Boolean,
    val sound: Boolean,
    @SerialName("daily_limit_minutes") val dailyLimitMinutes: Int,
    @SerialName("self_excluded") val selfExcluded: Boolean
) {
    fun toDomain(): AppSettings = AppSettings(
        darkModeEnabled = darkMode,
        notificationsEnabled = notifications,
        soundEnabled = sound,
        dailyLimitMinutes = dailyLimitMinutes,
        selfExclusionEnabled = selfExcluded
    )
}

/**
 * Real, network-backed [SettingsRepository] for signed-in Supabase users - each
 * user's preferences live in the `profiles` table (RLS-scoped to the caller via
 * `user_id`), one row per user, created on first read if it doesn't exist yet.
 * Guest sessions never touch Supabase: they delegate entirely to
 * [MockSettingsRepository] so guest mode keeps working without an account.
 *
 * Reads and writes fail soft (logged, not thrown) so a flaky connection never
 * crashes the profile screen; [observeSettings] falls back to whatever is
 * cached, and a failed write still lands optimistically in the in-session
 * cache so the toggle the player just tapped stays correct on screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SupabaseSettingsRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    private val mockSettingsRepository: MockSettingsRepository
) : SettingsRepository {

    private val remoteSettings = MutableStateFlow(AppSettings())
    private var cachedUserId: String? = null

    override fun observeSettings(): Flow<AppSettings> =
        authRepository.observeCurrentUser()
            .distinctUntilChanged { old, new -> old?.id == new?.id && old?.isGuest == new?.isGuest }
            .flatMapLatest { user ->
                when {
                    user == null -> flowOf(AppSettings())
                    user.isGuest -> mockSettingsRepository.observeSettings()
                    else -> remoteSettingsFlow(user)
                }
            }

    override suspend fun setDarkModeEnabled(enabled: Boolean) {
        withRemoteUserOrMock(
            onGuestOrLoggedOut = { mockSettingsRepository.setDarkModeEnabled(enabled) }
        ) { userId ->
            supabaseClient.from(TABLE).update(DarkModeUpdate(darkMode = enabled, updatedAt = nowIso())) {
                filter { eq("user_id", userId) }
            }
        }
        remoteSettings.update { it.copy(darkModeEnabled = enabled) }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        withRemoteUserOrMock(
            onGuestOrLoggedOut = { mockSettingsRepository.setNotificationsEnabled(enabled) }
        ) { userId ->
            supabaseClient.from(TABLE).update(NotificationsUpdate(notifications = enabled, updatedAt = nowIso())) {
                filter { eq("user_id", userId) }
            }
        }
        remoteSettings.update { it.copy(notificationsEnabled = enabled) }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        withRemoteUserOrMock(
            onGuestOrLoggedOut = { mockSettingsRepository.setSoundEnabled(enabled) }
        ) { userId ->
            supabaseClient.from(TABLE).update(SoundUpdate(sound = enabled, updatedAt = nowIso())) {
                filter { eq("user_id", userId) }
            }
        }
        remoteSettings.update { it.copy(soundEnabled = enabled) }
    }

    override suspend fun setDailyLimitMinutes(minutes: Int) {
        withRemoteUserOrMock(
            onGuestOrLoggedOut = { mockSettingsRepository.setDailyLimitMinutes(minutes) }
        ) { userId ->
            supabaseClient.from(TABLE).update(
                DailyLimitUpdate(dailyLimitMinutes = minutes, updatedAt = nowIso())
            ) {
                filter { eq("user_id", userId) }
            }
        }
        remoteSettings.update { it.copy(dailyLimitMinutes = minutes) }
    }

    override suspend fun enableSelfExclusion(): Result<Unit> {
        val user = authRepository.observeCurrentUser().first()
        if (user == null || user.isGuest) return mockSettingsRepository.enableSelfExclusion()
        return try {
            val rows = supabaseClient.from(TABLE).update(
                SelfExclusionUpdate(selfExcluded = true, updatedAt = nowIso())
            ) {
                select()
                filter { eq("user_id", user.id) }
            }.decodeList<ProfileRow>()
            // Updating no rows isn't an error, but nothing was saved.
            check(rows.isNotEmpty()) { "No profile row for ${user.id}" }
            remoteSettings.update { it.copy(selfExclusionEnabled = true) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save self-exclusion for ${user.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Runs [onRemote] against Supabase for the current signed-in, non-guest user,
     * or [onGuestOrLoggedOut] otherwise. Remote failures are logged, not thrown -
     * callers still apply the change to [remoteSettings] optimistically.
     */
    private suspend inline fun withRemoteUserOrMock(
        onGuestOrLoggedOut: () -> Unit,
        onRemote: (userId: String) -> Unit
    ) {
        val user = authRepository.observeCurrentUser().first()
        if (user == null || user.isGuest) {
            onGuestOrLoggedOut()
            return
        }
        try {
            onRemote(user.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist setting for ${user.id}", e)
        }
    }

    private fun remoteSettingsFlow(user: AuthUser): Flow<AppSettings> = flow {
        refreshFromDb(user)
        emitAll(remoteSettings)
    }

    private suspend fun refreshFromDb(user: AuthUser) {
        if (cachedUserId == user.id) return
        try {
            val rows = supabaseClient.from(TABLE)
                .select {
                    filter { eq("user_id", user.id) }
                }
                .decodeList<ProfileRow>()
            val existing = rows.firstOrNull()
            remoteSettings.value = if (existing != null) {
                existing.toDomain()
            } else {
                createDefaultProfile(user)
            }
            cachedUserId = user.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline or a query/RLS error: keep whatever's cached (the
            // defaults) instead of crashing the profile screen.
            Log.w(TAG, "Failed to load profile for ${user.id}", e)
        }
    }

    private suspend fun createDefaultProfile(user: AuthUser): AppSettings {
        val defaults = AppSettings()
        val row = ProfileInsertRow(
            id = "profile-${user.id}",
            userId = user.id,
            displayName = user.displayName,
            darkMode = defaults.darkModeEnabled,
            notifications = defaults.notificationsEnabled,
            sound = defaults.soundEnabled,
            dailyLimitMinutes = defaults.dailyLimitMinutes,
            selfExcluded = defaults.selfExclusionEnabled,
            updatedAt = nowIso()
        )
        supabaseClient.from(TABLE).insert(row)
        return defaults
    }

    private fun nowIso(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
