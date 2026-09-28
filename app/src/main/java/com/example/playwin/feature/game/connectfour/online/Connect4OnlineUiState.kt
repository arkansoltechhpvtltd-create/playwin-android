package com.example.playwin.feature.game.connectfour.online

import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus

/** Give up searching after this long with nobody to pair with. */
const val MATCHMAKING_TIMEOUT_SECONDS = 60

/** How long an opponent may be absent from an active room before the game is called off. */
const val OPPONENT_DISCONNECT_GRACE_SECONDS = 30

enum class Connect4OnlinePhase {
    /** Not searching and not in a room - shows the single "Find a random player" button. */
    IDLE,

    /** In the server's matchmaking queue, waiting to be paired. */
    SEARCHING,

    /** The search timed out with nobody to pair with. */
    NO_OPPONENT_FOUND,

    /** Paired into [Connect4OnlineUiState.room]. */
    ROOM
}

data class Connect4OnlineUiState(
    /** False once we know the signed-in session is a guest (or nobody's signed in) - online play needs a real account. */
    val canPlayOnline: Boolean = true,
    val myId: String? = null,
    val phase: Connect4OnlinePhase = Connect4OnlinePhase.IDLE,
    val searchSecondsElapsed: Int = 0,
    val room: Room? = null,
    val opponentOnline: Boolean = false,
    /** Whether the opponent has been present in the room at least once - distinguishes "still connecting" from "dropped". */
    val opponentEverSeen: Boolean = false,
    /** Seconds until an absent opponent forfeits the room, or null while they're present. */
    val disconnectSecondsLeft: Int? = null,
    val isResolving: Boolean = false,
    val errorMessage: String? = null,
    val wonCouponId: String? = null
) {
    val myPlayerNumber: Int?
        get() = when (myId) {
            null -> null
            room?.hostId -> 1
            room?.guestId -> 2
            else -> null
        }

    val isMyTurn: Boolean
        get() {
            val currentRoom = room ?: return false
            return currentRoom.status == RoomStatus.ACTIVE && currentRoom.currentTurn == myId
        }

    val opponentId: String?
        get() = room?.let { if (it.hostId == myId) it.guestId else it.hostId }
}
