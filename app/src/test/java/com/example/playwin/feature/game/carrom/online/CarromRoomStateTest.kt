package com.example.playwin.feature.game.carrom.online

import com.example.playwin.core.domain.carrom.CarromMatch
import com.example.playwin.core.domain.carrom.CoinColor
import com.example.playwin.core.domain.carrom.STRIKER_ID
import com.example.playwin.core.domain.carrom.Vec2
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarromRoomStateTest {

    private fun room(state: JsonElement) = Room(
        id = "r", hostId = "host", guestId = "guest", status = RoomStatus.ACTIVE,
        currentTurn = "host", state = state, winnerId = null
    )

    @Test
    fun `a fresh room is the opening setup with player 1 to shoot`() {
        assertEquals(CarromMatch(), room(JsonObject(emptyMap())).carromState().toMatch())
    }

    @Test
    fun `a match survives a round trip, with the shot that produced it`() {
        val match = CarromMatch().let { m ->
            m.copy(
                coins = m.coins.map { if (it.id == 5) it.copy(pocketed = true) else it },
                currentPlayer = 2,
                playerColors = mapOf(1 to CoinColor.WHITE, 2 to CoinColor.BLACK),
                scores = mapOf(1 to 4, 2 to 1)
            )
        }
        val shot = CarromRoomShot(seq = 3, shooter = 1, dx = 0.6f, dy = -0.8f, power = 0.75f)
        val decoded = room(CarromRoomState.of(match, shot, fouled = true).toJson()).carromState()
        assertEquals(match, decoded.toMatch())
        assertEquals(shot, decoded.shot)
        assertEquals(true, decoded.fouled)
    }

    @Test
    fun `dues owed survive a round trip`() {
        val match = CarromMatch(playerColors = mapOf(1 to CoinColor.WHITE, 2 to CoinColor.BLACK), duesOwed = mapOf(2 to 1))
        val decoded = room(CarromRoomState.of(match, null, fouled = true).toJson()).carromState()
        assertEquals(match, decoded.toMatch())
    }

    @Test
    fun `where the striker was slid to survives a round trip`() {
        val shot = CarromRoomShot(seq = 1, shooter = 2, dx = 0f, dy = 1f, power = 0.5f, sx = 250f)
        val decoded = room(CarromRoomState.of(CarromMatch(), shot, fouled = false).toJson()).carromState()
        assertEquals(250f, decoded.shot?.sx)
    }

    @Test
    fun `a shot from the centre is stored exactly as before, without a striker position`() {
        val shot = CarromRoomShot(seq = 1, shooter = 1, dx = 0f, dy = -1f, power = 1f)
        val stored = CarromRoomState.of(CarromMatch(), shot, fouled = false).toJson() as JsonObject
        assertEquals(setOf("seq", "shooter", "dx", "dy", "power"), (stored["shot"] as JsonObject).keys)
    }

    @Test
    fun `a stored shot from before sliding existed still decodes, from the centre`() {
        val old = buildJsonObject {
            put("shot", buildJsonObject {
                put("seq", 4); put("shooter", 1); put("dx", 0f); put("dy", -1f); put("power", 0.9f)
            })
        }
        assertNull(room(old).carromState().shot?.sx)
    }

    @Test
    fun `a replay starts with the striker where the shooter slid it`() {
        val coins = CarromMatch().coins
        val baseline = coins.first { it.id == STRIKER_ID }.position
        val shot = CarromRoomShot(seq = 1, shooter = 1, dx = 0f, dy = -1f, power = 1f, sx = 200f)
        assertEquals(Vec2(200f, baseline.y), shot.startingFrom(coins).first { it.id == STRIKER_ID }.position)
        assertEquals(coins, shot.copy(sx = null).startingFrom(coins))
    }
}
