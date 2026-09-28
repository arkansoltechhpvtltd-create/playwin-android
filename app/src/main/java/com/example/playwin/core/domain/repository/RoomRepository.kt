package com.example.playwin.core.domain.repository

import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * Random matchmaking into online-multiplayer rooms. There is deliberately no
 * way to create a room or name an opponent: the server pairs whoever is
 * waiting, so two players who know each other can't choose to play each
 * other. Nothing here is game-specific - each game stores its own board
 * shape in [Room.state].
 *
 * Today [com.example.playwin.core.data.remote.SupabaseRoomRepository] is the
 * only implementation, and it requires a real (non-guest) signed-in user,
 * since the backend keys everything off `auth.uid()`.
 */
interface RoomRepository {
    /**
     * Enters (or stays in) the matchmaking queue for [gameId]. Call it
     * repeatedly while searching - each call is also the heartbeat that keeps
     * this player pairable. Returns the paired room once the server has
     * matched this player with a random opponent, or null while still waiting.
     */
    suspend fun matchmake(gameId: GameId): Result<Room?>

    /** Leaves the matchmaking queue for [gameId]. Safe to call when not queued. */
    suspend fun cancelMatchmaking(gameId: GameId): Result<Unit>

    /** Opens a live subscription to [room], tracking [selfUserId]'s presence in it. */
    suspend fun connect(room: Room, selfUserId: String): RoomConnection
}

/** A live view of one room: its row changes and which of its two players are currently connected. */
interface RoomConnection {
    /** Emits the room's current state, then again every time it changes. */
    val roomUpdates: Flow<Room>

    /** The set of player ids currently present in the room's realtime channel. */
    val presentUserIds: Flow<Set<String>>

    /**
     * Writes the result of a move. The backend rejects this unless the caller
     * is the room's current [Room.currentTurn] player and it's [RoomStatus.ACTIVE] -
     * this call can fail with a rejection even if the local UI thought it was
     * this player's turn, which is the point: the row decides, not the client.
     */
    suspend fun submitMove(
        state: JsonElement,
        nextTurn: String?,
        status: RoomStatus,
        winnerId: String?
    ): Result<Unit>

    /** Marks the room abandoned - call when the local player deliberately leaves mid-game. */
    suspend fun markAbandoned(): Result<Unit>

    /** Stops the subscription and releases the realtime channel. Safe to call more than once. */
    suspend fun close()
}
