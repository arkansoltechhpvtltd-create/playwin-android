package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.UserProfile
import com.example.playwin.core.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockProfileRepository @Inject constructor() : ProfileRepository {

    private val profile = MutableStateFlow(
        UserProfile(
            name = "Arka Sol",
            initials = "AS",
            ageVerified = true,
            playtimeTodayMinutes = 42,
            dailyLimitMinutes = 120
        )
    ).asStateFlow()

    override fun observeProfile(): Flow<UserProfile> = profile
}
