package com.example.playwin.core.domain.repository

import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot

/**
 * The single authority for "who won".
 *
 * Composables never inspect a board and declare a winner, and ViewModels only
 * ever *ask* this repository what a snapshot resolves to - they don't decide
 * it themselves. Today [com.example.playwin.core.data.mock.MockGameEngineRepository]
 * answers locally; a real build swaps it for a network call to a
 * server-authoritative engine with no changes to any ViewModel or screen.
 */
interface GameEngineRepository {
    suspend fun resolve(gameId: GameId, snapshot: GameSnapshot): GameOutcome

    /**
     * Throws one six-sided die for [gameId] and returns the face, 1..6. Dice
     * games get their roll from here rather than rolling in a ViewModel or
     * screen, so the throw can move server-side along with [resolve].
     */
    suspend fun rollDie(gameId: GameId): Int
}
