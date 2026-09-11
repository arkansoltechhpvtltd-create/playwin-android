package com.example.playwin.core.domain.model

/** App-wide preferences and responsible-gaming controls, kept for the signed-in session. */
data class AppSettings(
    val darkModeEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val dailyLimitMinutes: Int = 120,
    val selfExclusionEnabled: Boolean = false
)
