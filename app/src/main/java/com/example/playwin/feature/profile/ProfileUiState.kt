package com.example.playwin.feature.profile

data class ProfileUiState(
    val displayName: String = "",
    val initials: String = "",
    val identifier: String = "",
    val isGuest: Boolean = false,
    val isEditingName: Boolean = false,
    val editedName: String = "",
    val nameError: String? = null,
    /** Null until playtime is actually tracked, so no made-up figure is ever shown. */
    val playtimeTodayMinutes: Int? = null,
    val darkModeEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val dailyLimitMinutes: Int = 120,
    val selfExclusionEnabled: Boolean = false,
    val selfExclusionError: String? = null
)
