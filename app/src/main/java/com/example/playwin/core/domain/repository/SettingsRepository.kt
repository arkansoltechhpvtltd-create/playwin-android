package com.example.playwin.core.domain.repository

import com.example.playwin.core.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/** App preferences and responsible-gaming controls for the current session. */
interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>

    suspend fun setDarkModeEnabled(enabled: Boolean)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setDailyLimitMinutes(minutes: Int)

    /** One-way: only support can lift it. Fails if it didn't save, so a player is never told they're excluded when they aren't. */
    suspend fun enableSelfExclusion(): Result<Unit>
}
