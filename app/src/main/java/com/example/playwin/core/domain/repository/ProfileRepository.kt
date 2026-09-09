package com.example.playwin.core.domain.repository

import com.example.playwin.core.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfile(): Flow<UserProfile>
}
