package com.example.playwin.feature.game.online

import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus

/** Give up searching after this long with nobody to pair with. */
const val MATCHMAKING_TIMEOUT_SECONDS = 60

/** How long an opponent may be absent from an active room before the game is called off. */
const val OPPONENT_DISCONNECT_GRACE_SECONDS = 30

enum class OnlinePhase {
    /** Not searching and not in a room - shows the single "Find a random player" button. */
    IDLE,

    /** In the server's matchmaking queue, waiting to be paired. */
    SEARCHING,

    /** The search timed out with nobody to pair with. */
    NO_OPPONENT_FOUND,

    /** Paired into [OnlineMatchState.room]. */
    ROOM
}

/**
 * Game-agnostic state of one player's online session: matchmaking, the
 * paired [Room], and the opponent's connection. Each game reads its own
 * board out of [Room.state]; everything else here is shared by every game.
 */
data class OnlineMatchState(
    /** False once we know the signed-in session is a guest (or nobody's signed in) - online play needs a real account. */
    val canPlayOnline: Boolean = true,
    val myId: String? = null,
    val phase: OnlinePhase = OnlinePhase.IDLE,
    val searchSecondsElapsed: Int = 0,
    val room: Room? = null,
    val opponentOnline: Boolean = false,
    /** Whether the opponent has been present in the room at least once - distinguishes "still connecting" from "dropped". */
    val opponentEverSeen: Boolean = false,
    /** Seconds until an absent opponent forfeits the room, or null while they're present. */
    val disconnectSecondsLeft: Int? = null,
    /** A move is being resolved/submitted - input should be disabled. */
    val isResolving: Boolean = false,
    val errorMessage: String? = null,
    val wonCouponId: String? = null
) {
    /** 1 for the room's host (who always moves first), 2 for the guest. */
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

    /** The user id of player [number] (1 = host, 2 = guest). */
    fun playerId(number: Int): String? = when (number) {
        1 -> room?.hostId
        2 -> room?.guestId
        else -> null
    }
}
