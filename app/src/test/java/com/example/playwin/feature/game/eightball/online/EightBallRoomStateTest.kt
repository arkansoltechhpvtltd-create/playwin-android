package com.example.playwin.feature.game.eightball.online

import com.example.playwin.core.domain.eightball.BallGroup
import com.example.playwin.core.domain.eightball.EightBallMatch
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class EightBallRoomStateTest {

    private fun room(state: JsonElement) = Room(
        id = "r", hostId = "host", guestId = "guest", status = RoomStatus.ACTIVE,
        currentTurn = "host", state = state, winnerId = null
    )

    @Test
    fun `a fresh room is the opening rack with player 1 to break`() {
        assertEquals(EightBallMatch(), room(JsonObject(emptyMap())).eightBallState().toMatch())
    }

    @Test
    fun `a table survives a round trip, with the shot that produced it`() {
        val match = EightBallMatch().let { m ->
            m.copy(
                balls = m.balls.map { if (it.id == 4) it.copy(pocketed = true) else it },
                currentPlayer = 2,
                playerGroups = mapOf(1 to BallGroup.SOLIDS, 2 to BallGroup.STRIPES)
            )
        }
        val shot = EightBallRoomShot(seq = 5, shooter = 1, dx = 1f, dy = 0f, power = 0.5f)
        val decoded = room(EightBallRoomState.of(match, shot, scratched = false).toJson()).eightBallState()
        assertEquals(match, decoded.toMatch())
        assertEquals(shot, decoded.shot)
    }
}
