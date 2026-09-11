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
    suspend fun setSelfExclusionEnabled(enabled: Boolean)
}
