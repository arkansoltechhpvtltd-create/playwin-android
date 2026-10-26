package com.example.playwin.feature.game.snakesandladders.online

import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersMove
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading the board the server's `roll_and_move` writes (see 0009_snakes_and_ladders_server_dice.sql). */
class SnakesAndLaddersRoomStateTest {

    private fun room(state: JsonElement) = Room(
        id = "r", hostId = "host", guestId = "guest", status = RoomStatus.ACTIVE,
        currentTurn = "host", state = state, winnerId = null
    )

    /** Exactly the shape jsonb_build_object produces in roll_and_move. */
    private fun serverBoard(json: String) = room(Json.parseToJsonElement(json))

    @Test
    fun `a fresh room decodes to the starting position`() {
        val fresh = room(JsonObject(emptyMap()))
        assertEquals(SnakesAndLaddersState(), fresh.snakesAndLaddersState())
        assertNull(fresh.snakesAndLaddersLastMove())
    }

    @Test
    fun `the server's board decodes to the position and whose turn it is`() {
        val stored = serverBoard(
            """{"positions": [84, 40], "turn": 1, "sixes": 0, "turns": 7,
               "last": {"player": 0, "roll": 3, "from": 25, "forfeited": false}}"""
        )
        assertEquals(SnakesAndLaddersState(positions = listOf(84, 40), turn = 1, turnsPlayed = 7), stored.snakesAndLaddersState())
    }

    @Test
    fun `the stored roll replays as the same move, ladder included`() {
        val stored = serverBoard(
            """{"positions": [84, 40], "turn": 1, "sixes": 0, "turns": 7,
               "last": {"player": 0, "roll": 3, "from": 25, "forfeited": false}}"""
        )
        val move = stored.snakesAndLaddersLastMove()!!
        assertEquals(SnakesAndLaddersMove(player = 0, roll = 3, from = 25, landed = 28, to = 84), move)
        assertTrue(move.climbedLadder)
    }

    @Test
    fun `a six keeps the turn with the roller`() {
        val stored = serverBoard(
            """{"positions": [3, 31], "turn": 1, "sixes": 1, "turns": 4,
               "last": {"player": 1, "roll": 6, "from": 3, "forfeited": false}}"""
        ).snakesAndLaddersState()
        assertEquals(1, stored.turn)
        assertEquals(1, stored.sixesInARow)
    }

    @Test
    fun `a forfeited roll replays as no move`() {
        val move = serverBoard(
            """{"positions": [10, 0], "turn": 1, "sixes": 0, "turns": 9,
               "last": {"player": 0, "roll": 6, "from": 10, "forfeited": true}}"""
        ).snakesAndLaddersLastMove()!!
        assertTrue(move.forfeited)
        assertEquals(10, move.to)
    }

    @Test
    fun `a malformed board falls back to the start rather than crashing`() {
        val stored = serverBoard("""{"positions": [5], "turn": 0}""")
        assertEquals(SnakesAndLaddersState(), stored.snakesAndLaddersState())
    }

    @Test
    fun `host rolls first as player index 0`() {
        assertEquals(0, snakesAndLaddersPlayerIndex(1))
        assertEquals(1, snakesAndLaddersPlayerIndex(2))
        assertEquals(1, snakesAndLaddersPlayerNumber(0))
        assertEquals(2, snakesAndLaddersPlayerNumber(1))
    }
}
