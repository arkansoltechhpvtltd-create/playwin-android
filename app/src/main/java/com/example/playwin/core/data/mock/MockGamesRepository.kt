package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.Game
import com.example.playwin.core.domain.model.GameIconKey
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.repository.GamesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory catalogue of the 9 lobby games. No backend involved. */
@Singleton
class MockGamesRepository @Inject constructor() : GamesRepository {

    private val games = MutableStateFlow(
        listOf(
            Game(
                id = GameId.TWENTY_FORTY_EIGHT,
                title = "2048",
                tagline = "Merge to win",
                icon = GameIconKey.GRID,
                isFullyPlayable = true
            ),
            Game(
                id = GameId.EIGHT_BALL,
                title = "8-Ball",
                tagline = "Pocket them all",
                icon = GameIconKey.BILLIARDS,
                isFullyPlayable = false
            ),
            Game(
                id = GameId.CARROM,
                title = "Carrom",
                tagline = "Strike & pot",
                icon = GameIconKey.CARROM,
                isFullyPlayable = false
            ),
            Game(
                id = GameId.CHINESE_CHECKERS,
                title = "Chinese Checkers",
                tagline = "Hop to the far point",
                icon = GameIconKey.CHINESE_CHECKERS,
                isFullyPlayable = true
            ),
            Game(
                id = GameId.SUDOKU,
                title = "Sudoku",
                tagline = "Fill the grid",
                icon = GameIconKey.SUDOKU,
                isFullyPlayable = true
            ),
            Game(
                id = GameId.WORD_HUNT,
                title = "Word Hunt",
                tagline = "Find the words",
                icon = GameIconKey.WORD_HUNT,
                isFullyPlayable = false
            ),
            Game(
                id = GameId.CHESS,
                title = "Chess",
                tagline = "Outsmart them",
                icon = GameIconKey.CHESS,
                isFullyPlayable = false
            ),
            Game(
                id = GameId.LUDO,
                title = "Ludo",
                tagline = "Race home first",
                icon = GameIconKey.LUDO,
                isFullyPlayable = false
            ),
            Game(
                id = GameId.RUMMY,
                title = "Rummy",
                tagline = "Build your sets",
                icon = GameIconKey.CARDS,
                isFullyPlayable = false
            )
        )
    ).asStateFlow()

    override fun observeGames(): Flow<List<Game>> = games
}
