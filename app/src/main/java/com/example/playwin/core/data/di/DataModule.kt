package com.example.playwin.core.data.di

import com.example.playwin.core.data.mock.MockGameEngineRepository
import com.example.playwin.core.data.mock.MockRewardsRepository
import com.example.playwin.core.data.remote.SupabaseAuthRepository
import com.example.playwin.core.data.remote.SupabaseCouponsRepository
import com.example.playwin.core.data.remote.SupabaseGamesRepository
import com.example.playwin.core.data.remote.SupabaseRoomRepository
import com.example.playwin.core.data.remote.SupabaseSettingsRepository
import com.example.playwin.core.domain.repository.AuthRepository
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.repository.GamesRepository
import com.example.playwin.core.domain.repository.RewardsRepository
import com.example.playwin.core.domain.repository.RoomRepository
import com.example.playwin.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds each domain repository interface to its implementation. This is the
 * only place that needs to change to swap a mock for a real network-backed
 * repository - [AuthRepository], [CouponsRepository], [SettingsRepository]
 * and [GamesRepository] are already live on Supabase; the rest are still
 * in-memory mocks.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindGamesRepository(impl: SupabaseGamesRepository): GamesRepository

    @Binds
    @Singleton
    abstract fun bindCouponsRepository(impl: SupabaseCouponsRepository): CouponsRepository

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
    abstract fun bindSettingsRepository(impl: SupabaseSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindRoomRepository(impl: SupabaseRoomRepository): RoomRepository
}
