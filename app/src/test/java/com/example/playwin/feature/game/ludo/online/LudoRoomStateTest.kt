package com.example.playwin.feature.game.ludo.online

import com.example.playwin.core.domain.ludo.LudoColor
import com.example.playwin.core.domain.ludo.LudoEngine
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LudoRoomStateTest {

    private fun room(state: JsonElement) = Room(
        id = "r", hostId = "host", guestId = "guest", status = RoomStatus.ACTIVE,
        currentTurn = "host", state = state, winnerId = null
    )

    @Test
    fun `a fresh room decodes to the two-player starting position`() {
        val game = room(JsonObject(emptyMap())).ludoState()
        assertEquals(LudoEngine.initialState(LudoEngine.TWO_PLAYER), game)
        assertNull(room(JsonObject(emptyMap())).ludoLastRoll())
    }

    @Test
    fun `state survives a round trip through the room`() {
        val start = LudoEngine.initialState(LudoEngine.TWO_PLAYER).copy(diceValue = 6, hasRolled = true)
        val moved = LudoEngine.applyMove(start, LudoColor.RED, 2)

        val decoded = room(moved.toRoomJson(lastRoll = 6)).ludoState()
        assertEquals(moved, decoded)
        assertEquals(6, room(moved.toRoomJson(lastRoll = 6)).ludoLastRoll())
    }

    @Test
    fun `host is red and guest is yellow`() {
        assertEquals(LudoColor.RED, ludoColorOf(1))
        assertEquals(LudoColor.YELLOW, ludoColorOf(2))
        assertEquals(1, ludoPlayerNumber(LudoColor.RED))
        assertEquals(2, ludoPlayerNumber(LudoColor.YELLOW))
    }
}
