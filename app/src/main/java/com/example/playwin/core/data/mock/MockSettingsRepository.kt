package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.AppSettings
import com.example.playwin.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory app settings, kept for the lifetime of the process. */
@Singleton
class MockSettingsRepository @Inject constructor() : SettingsRepository {

    private val settings = MutableStateFlow(AppSettings())

    override fun observeSettings(): Flow<AppSettings> = settings.asStateFlow()

    override suspend fun setDarkModeEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(darkModeEnabled = enabled)
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(notificationsEnabled = enabled)
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(soundEnabled = enabled)
    }

    override suspend fun setDailyLimitMinutes(minutes: Int) {
        settings.value = settings.value.copy(dailyLimitMinutes = minutes)
    }

    override suspend fun setSelfExclusionEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(selfExclusionEnabled = enabled)
    }
}
