package com.example.playwin.core.domain.repository

import com.example.playwin.core.domain.model.Game
import kotlinx.coroutines.flow.Flow

/** Source of the catalogue of games shown in the lobby. */
interface GamesRepository {
    fun observeGames(): Flow<List<Game>>
}
