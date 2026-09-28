package com.example.playwin.feature.game.bingo.online

import com.example.playwin.core.domain.bingo.BingoRules
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BingoRoomStateTest {

    private fun room(state: JsonElement) = Room(
        id = "room-42", hostId = "host", guestId = "guest", status = RoomStatus.ACTIVE,
        currentTurn = "host", state = state, winnerId = null
    )

    @Test
    fun `a fresh room has nothing called`() {
        assertEquals(BingoRoomState(), room(JsonObject(emptyMap())).bingoState())
    }

    @Test
    fun `calls survive a round trip through the room`() {
        val stored = BingoRoomState(called = listOf(7, 22, 61), lastCaller = 2)
        assertEquals(stored, room(stored.toJson()).bingoState())
    }

    @Test
    fun `host and guest get the room's two derived cards`() {
        val r = room(JsonObject(emptyMap()))
        val (host, guest) = BingoRules.cardsForRoom("room-42")
        assertEquals(host, bingoCardFor(r, 1))
        assertEquals(guest, bingoCardFor(r, 2))
    }
}
