package com.example.playwin.core.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomRequestEncodingTest {

    /** The Json supabase-kt encodes request bodies with by default (SupabaseClientBuilder.defaultSerializer). */
    private val supabaseJson = Json { ignoreUnknownKeys = true }

    @Test
    fun `leaving a room really sends the abandoned status`() {
        assertEquals("""{"status":"abandoned"}""", supabaseJson.encodeToString(RoomAbandonUpdate.serializer(), RoomAbandonUpdate(status = "abandoned")))
    }
}
