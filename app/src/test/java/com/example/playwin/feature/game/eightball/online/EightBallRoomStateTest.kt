package com.example.playwin.feature.game.eightball.online

import com.example.playwin.core.domain.eightball.BallGroup
import com.example.playwin.core.domain.eightball.BallInHand
import com.example.playwin.core.domain.eightball.EightBallFoul
import com.example.playwin.core.domain.eightball.Vec2
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
                playerGroups = mapOf(1 to BallGroup.SOLIDS, 2 to BallGroup.STRIPES),
                isBreak = false
            )
        }
        val shot = EightBallRoomShot(seq = 5, shooter = 1, dx = 1f, dy = 0f, power = 0.5f)
        val decoded = room(EightBallRoomState.of(match, shot, foul = null).toJson()).eightBallState()
        assertEquals(match, decoded.toMatch())
        assertEquals(shot, decoded.shot)
    }

    @Test
    fun `ball in hand and where the cue ball was placed survive a round trip`() {
        val match = EightBallMatch(isBreak = false, currentPlayer = 2, ballInHand = BallInHand.ANYWHERE)
        val shot = EightBallRoomShot(seq = 4, shooter = 1, dx = 0f, dy = 1f, power = 0.6f, cx = 300f, cy = 120f)
        val decoded = room(EightBallRoomState.of(match, shot, EightBallFoul.NO_RAIL).toJson()).eightBallState()
        assertEquals(match, decoded.toMatch())
        assertEquals(Vec2(300f, 120f), decoded.shot?.cueAt)
    }

    @Test
    fun `a shot without a placement is stored as before`() {
        val shot = EightBallRoomShot(seq = 1, shooter = 1, dx = 1f, dy = 0f, power = 1f)
        val stored = EightBallRoomState.of(EightBallMatch(), shot, foul = null).toJson() as JsonObject
        assertEquals(setOf("seq", "shooter", "dx", "dy", "power"), (stored["shot"] as JsonObject).keys)
    }
}
