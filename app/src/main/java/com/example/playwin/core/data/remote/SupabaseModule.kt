package com.example.playwin.core.data.remote

import com.example.playwin.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import javax.inject.Singleton

/**
 * Provides the single [SupabaseClient] for the app, configured from
 * `local.properties` (via [BuildConfig]) so the URL/key are never hardcoded
 * or committed. [Realtime] powers the Connect 4 online-multiplayer room
 * subscription; it reuses whatever session [Auth] currently holds, so a
 * signed-in user's JWT (and therefore the `rooms` table's RLS) applies to
 * its socket automatically.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }
}
