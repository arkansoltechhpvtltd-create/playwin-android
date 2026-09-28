package com.example.playwin.feature.game.carrom.online

import com.example.playwin.core.domain.carrom.CarromMatch
import com.example.playwin.core.domain.carrom.CoinColor
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
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
}
