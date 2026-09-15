package com.example.playwin.core.data.di

import com.example.playwin.core.data.mock.MockCouponsRepository
import com.example.playwin.core.data.mock.MockGameEngineRepository
import com.example.playwin.core.data.mock.MockGamesRepository
import com.example.playwin.core.data.mock.MockRewardsRepository
import com.example.playwin.core.data.mock.MockSettingsRepository
import com.example.playwin.core.data.remote.SupabaseAuthRepository
import com.example.playwin.core.domain.repository.AuthRepository
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.repository.GamesRepository
import com.example.playwin.core.domain.repository.RewardsRepository
import com.example.playwin.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds each domain repository interface to its implementation. This is the
 * only place that needs to change to swap a mock for a real network-backed
 * repository - [AuthRepository] is already live on Supabase; the rest are
 * still in-memory mocks.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindGamesRepository(impl: MockGamesRepository): GamesRepository

    @Binds
    @Singleton
    abstract fun bindCouponsRepository(impl: MockCouponsRepository): CouponsRepository

    @Binds
    @Singleton
    abstract fun bindRewardsRepository(impl: MockRewardsRepository): RewardsRepository

    @Binds
    @Singleton
    abstract fun bindGameEngineRepository(impl: MockGameEngineRepository): GameEngineRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: MockSettingsRepository): SettingsRepository
}
