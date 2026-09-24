package com.example.playwin.feature.game.connectfour.online

import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus

enum class Connect4OnlinePhase { MENU, ENTER_CODE, CREATING, JOINING, ROOM }

data class Connect4OnlineUiState(
    /** False once we know the signed-in session is a guest (or nobody's signed in) - online play needs a real account. */
    val canPlayOnline: Boolean = true,
    val myId: String? = null,
    val phase: Connect4OnlinePhase = Connect4OnlinePhase.MENU,
    val room: Room? = null,
    val opponentOnline: Boolean = false,
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
