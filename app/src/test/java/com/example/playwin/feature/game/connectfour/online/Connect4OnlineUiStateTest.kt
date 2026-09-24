package com.example.playwin.feature.game.connectfour.online

import com.example.playwin.core.domain.connectfour.Connect4Logic
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Connect4OnlineUiStateTest {

    private fun room(
        status: RoomStatus = RoomStatus.ACTIVE,
        hostId: String = "host",
        guestId: String? = "guest",
        currentTurn: String? = "host"
    ) = Room(
        id = "room-1",
        code = "ABC123",
        hostId = hostId,
        guestId = guestId,
        status = status,
        currentTurn = currentTurn,
        board = Connect4Logic.emptyBoard(),
        winnerId = null
    )

    @Test
    fun `host is player one and guest is player two`() {
        val state = Connect4OnlineUiState(myId = "host", room = room())
        assertEquals(1, state.myPlayerNumber)

        val guestState = Connect4OnlineUiState(myId = "guest", room = room())
        assertEquals(2, guestState.myPlayerNumber)
    }

    @Test
    fun `it is only my turn when the room is active and current_turn matches me`() {
        val myTurn = Connect4OnlineUiState(myId = "host", room = room(currentTurn = "host"))
        assertTrue(myTurn.isMyTurn)

        val opponentsTurn = Connect4OnlineUiState(myId = "host", room = room(currentTurn = "guest"))
        assertFalse(opponentsTurn.isMyTurn)

        val finished = Connect4OnlineUiState(
            myId = "host",
            room = room(status = RoomStatus.FINISHED, currentTurn = "host")
        )
        assertFalse(finished.isMyTurn)
    }

    @Test
    fun `opponentId resolves to whichever side I am not`() {
        val asHost = Connect4OnlineUiState(myId = "host", room = room())
        assertEquals("guest", asHost.opponentId)

        val asGuest = Connect4OnlineUiState(myId = "guest", room = room())
        assertEquals("host", asGuest.opponentId)
    }

    @Test
    fun `no room means no player number and no turn`() {
        val state = Connect4OnlineUiState(myId = "host", room = null)
        assertNull(state.myPlayerNumber)
        assertFalse(state.isMyTurn)
        assertNull(state.opponentId)
    }
}
