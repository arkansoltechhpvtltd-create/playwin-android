package com.example.playwin.feature.game.online

import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineMatchStateTest {

    private fun room(
        status: RoomStatus = RoomStatus.ACTIVE,
        hostId: String = "host",
        guestId: String? = "guest",
        currentTurn: String? = "host"
    ) = Room(
        id = "room-1",
        hostId = hostId,
        guestId = guestId,
        status = status,
        currentTurn = currentTurn,
        state = JsonObject(emptyMap()),
        winnerId = null
    )

    @Test
    fun `host is player one and guest is player two`() {
        val state = OnlineMatchState(myId = "host", room = room())
        assertEquals(1, state.myPlayerNumber)

        val guestState = OnlineMatchState(myId = "guest", room = room())
        assertEquals(2, guestState.myPlayerNumber)
    }

    @Test
    fun `it is only my turn when the room is active and current_turn matches me`() {
        val myTurn = OnlineMatchState(myId = "host", room = room(currentTurn = "host"))
        assertTrue(myTurn.isMyTurn)

        val opponentsTurn = OnlineMatchState(myId = "host", room = room(currentTurn = "guest"))
        assertFalse(opponentsTurn.isMyTurn)

        val finished = OnlineMatchState(
            myId = "host",
            room = room(status = RoomStatus.FINISHED, currentTurn = "host")
        )
        assertFalse(finished.isMyTurn)
    }

    @Test
    fun `opponentId resolves to whichever side I am not`() {
        val asHost = OnlineMatchState(myId = "host", room = room())
        assertEquals("guest", asHost.opponentId)

        val asGuest = OnlineMatchState(myId = "guest", room = room())
        assertEquals("host", asGuest.opponentId)
    }

    @Test
    fun `no room means no player number and no turn`() {
        val state = OnlineMatchState(myId = "host", room = null)
        assertNull(state.myPlayerNumber)
        assertFalse(state.isMyTurn)
        assertNull(state.opponentId)
    }

    @Test
    fun `playerId maps player numbers to the room's user ids`() {
        val state = OnlineMatchState(myId = "guest", room = room())
        assertEquals("host", state.playerId(1))
        assertEquals("guest", state.playerId(2))
        assertNull(state.playerId(3))
    }
}
