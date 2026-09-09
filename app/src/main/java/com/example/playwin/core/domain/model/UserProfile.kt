package com.example.playwin.core.domain.model

data class UserProfile(
    val name: String,
    val initials: String,
    val ageVerified: Boolean,
    val playtimeTodayMinutes: Int,
    val dailyLimitMinutes: Int
)
