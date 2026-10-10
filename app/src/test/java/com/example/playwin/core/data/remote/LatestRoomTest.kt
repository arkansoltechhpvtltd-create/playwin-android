package com.example.playwin.core.data.remote

import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class LatestRoomTest {

    private fun room(move: Int, turn: String = "host") =
        Room(id = "r", hostId = "host", guestId = "guest", status = RoomStatus.ACTIVE, currentTurn = turn, state = JsonPrimitive(move), winnerId = null)

    @Test
    fun `a re-read catches up on a move Realtime never delivered`() = runBlocking {
        val latest = LatestRoom()
        latest.room.value = room(0)

        latest.reread { room(1, turn = "guest") }

        assertEquals(room(1, turn = "guest"), latest.room.value)
    }

    @Test
    fun `a live update that lands mid re-read wins over the older row`() = runBlocking {
        val latest = LatestRoom()
        latest.room.value = room(0)

        latest.reread {
            latest.fromRealtime(room(2)) // the newer move arrives while the read is in flight
            room(1)
        }

        assertEquals(room(2), latest.room.value)
    }

    @Test
    fun `a failed re-read changes nothing`() = runBlocking {
        val latest = LatestRoom()
        latest.room.value = room(3)

        latest.reread { null }

        assertEquals(room(3), latest.room.value)
    }

    @Test
    fun `live updates always apply, and a later re-read still can`() = runBlocking {
        val latest = LatestRoom()
        latest.fromRealtime(room(1))
        latest.fromRealtime(room(2))
        assertEquals(room(2), latest.room.value)

        latest.reread { room(3) }
        assertEquals(room(3), latest.room.value)
    }
}
