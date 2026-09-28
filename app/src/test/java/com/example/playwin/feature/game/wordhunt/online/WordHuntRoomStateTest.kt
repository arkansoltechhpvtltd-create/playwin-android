package com.example.playwin.feature.game.wordhunt.online

import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import com.example.playwin.core.domain.wordhunt.WordHuntClaim
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class WordHuntRoomStateTest {

    private fun room(state: JsonElement) = Room(
        id = "r", hostId = "host", guestId = "guest", status = RoomStatus.ACTIVE,
        currentTurn = "host", state = state, winnerId = null
    )

    @Test
    fun `a fresh room has no claims`() {
        assertEquals(WordHuntRoomState(), room(JsonObject(emptyMap())).wordHuntState())
    }

    @Test
    fun `claims survive a round trip and stay with their player`() {
        val stored = WordHuntRoomState()
            .withClaim(1, WordHuntClaim("LUDO", 4))
            .withClaim(2, WordHuntClaim("CHESS", 7))
        val decoded = room(stored.toJson()).wordHuntState()
        assertEquals(stored, decoded)
        assertEquals(listOf(WordHuntClaim("LUDO", 4)), decoded.claimsOf(1))
        assertEquals(listOf(WordHuntClaim("CHESS", 7)), decoded.claimsOf(2))
        assertEquals(setOf("LUDO", "CHESS"), decoded.claimedWords)
    }
}
