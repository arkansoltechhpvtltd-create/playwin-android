package com.example.playwin.core.domain.repository

import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import kotlinx.coroutines.flow.Flow

/**
 * Pairs two players into an online-multiplayer room via a short shareable
 * code. Today only Connect 4 uses this, but nothing here is Connect-4-specific
 * beyond [Room.board]'s shape - a second online game can reuse it.
 *
 * Today [com.example.playwin.core.data.remote.SupabaseRoomRepository] is the
 * only implementation, and it requires a real (non-guest) signed-in user,
 * since the backend RLS keying off `auth.uid()` has nothing to check a local
 * guest session against.
 */
interface RoomRepository {
    /** Creates a new waiting room hosted by [hostId] and returns it with its shareable code. */
    suspend fun createRoom(hostId: String): Result<Room>

    /** Joins the waiting room identified by [code] as [userId]. Fails if it's full, finished, or your own. */
    suspend fun joinRoom(userId: String, code: String): Result<Room>

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
        board: List<List<Int>>,
        nextTurn: String?,
        status: RoomStatus,
        winnerId: String?
    ): Result<Unit>

    /** Marks the room abandoned - call when the local player deliberately leaves mid-game. */
    suspend fun markAbandoned(): Result<Unit>

    /** Stops the subscription and releases the realtime channel. Safe to call more than once. */
    suspend fun close()
}
