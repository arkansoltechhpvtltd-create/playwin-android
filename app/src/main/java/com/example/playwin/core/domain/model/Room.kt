package com.example.playwin.core.domain.model

/** Lifecycle of an online-multiplayer room row. */
enum class RoomStatus {
    /** Host created the room; waiting for a second player to join with the code. */
    WAITING,

    /** Both players present; [Room.currentTurn] says who moves next. */
    ACTIVE,

    /** Someone won, or it was a draw - see [Room.winnerId] (null on a draw). */
    FINISHED,

    /** A player left before the game finished. */
    ABANDONED
}

/**
 * One online-multiplayer room, mirroring a row of the backend `rooms` table.
 * The row is the source of truth: a player id, not client state, decides
 * whose turn it is, and the backend rejects a move written out of turn - see
 * `supabase/migrations/0001_rooms.sql`.
 */
data class Room(
    val id: String,
    val code: String,
    val hostId: String,
    val guestId: String?,
    val status: RoomStatus,
    val currentTurn: String?,
    val board: List<List<Int>>,
    val winnerId: String?
)
