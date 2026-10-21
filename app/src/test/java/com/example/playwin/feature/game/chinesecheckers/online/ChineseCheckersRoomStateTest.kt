package com.example.playwin.feature.game.chinesecheckers.online

import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersRules
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChineseCheckersRoomStateTest {

    private fun room(state: JsonElement) = Room(
        id = "r", hostId = "host", guestId = "guest", status = RoomStatus.ACTIVE,
        currentTurn = "host", state = state, winnerId = null
    )

    @Test
    fun `a fresh room is the starting position with the host to move and no last move`() {
        val fresh = room(JsonObject(emptyMap()))
        assertEquals(ChineseCheckersRules.initialPosition(), fresh.chineseCheckersPosition())
        assertNull(fresh.chineseCheckersLastMove())
    }

    @Test
    fun `a position survives a round trip, turn count and the move that made it included`() {
        val start = ChineseCheckersRules.initialPosition()
        val jump = ChineseCheckersRules.legalMoves(start).first { it.isJump }
        val after = ChineseCheckersRules.play(start, jump)

        val stored = room(after.toRoomJson(jump))
        assertEquals(after, stored.chineseCheckersPosition())
        assertEquals(2, stored.chineseCheckersPosition().sideToMove)
        assertEquals(1, stored.chineseCheckersPosition().turnsPlayed)
        assertEquals(jump, stored.chineseCheckersLastMove())
    }

    @Test
    fun `a pass is stored with no last move to show`() {
        val passed = ChineseCheckersRules.pass(ChineseCheckersRules.initialPosition())
        val stored = room(passed.toRoomJson(lastMove = null))
        assertEquals(passed, stored.chineseCheckersPosition())
        assertNull(stored.chineseCheckersLastMove())
    }

    @Test
    fun `an unreadable or wrong-sized board falls back to the starting position`() {
        assertEquals(ChineseCheckersRules.initialPosition(), room(JsonPrimitive("junk")).chineseCheckersPosition())
        val tooShort = room(kotlinx.serialization.json.buildJsonObject {
            put("cells", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2))))
        })
        assertEquals(ChineseCheckersRules.initialPosition(), tooShort.chineseCheckersPosition())
    }
}
