package com.example.playwin.core.data.remote

import android.util.Log
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import com.example.playwin.core.domain.repository.RoomConnection
import com.example.playwin.core.domain.repository.RoomRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.presenceDataFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SupabaseRoom"
private const val TABLE = "rooms"

/** Thrown for expected matchmaking failures so screens can show their message. */
class RoomException(message: String) : Exception(message)

@Serializable
private data class MatchmakeParams(@SerialName("p_game_id") val gameId: String)

@Serializable
private data class RoomMoveUpdate(
    @SerialName("board_state") val boardState: List<List<Int>>,
    @SerialName("current_turn") val currentTurn: String?,
    val status: String,
    @SerialName("winner_id") val winnerId: String?
)

@Serializable
private data class RoomAbandonUpdate(val status: String = "abandoned")

@Serializable
private data class RoomRow(
    val id: String,
    @SerialName("host_id") val hostId: String,
    @SerialName("guest_id") val guestId: String? = null,
    val status: String,
    @SerialName("current_turn") val currentTurn: String? = null,
    @SerialName("board_state") val boardState: List<List<Int>> = emptyList(),
    @SerialName("winner_id") val winnerId: String? = null
) {
    fun toDomain(): Room = Room(
        id = id,
        hostId = hostId,
        guestId = guestId,
        status = runCatching { RoomStatus.valueOf(status.uppercase()) }.getOrDefault(RoomStatus.ABANDONED),
        currentTurn = currentTurn,
        board = boardState,
        winnerId = winnerId
    )
}

@Serializable
private data class PresenceData(@SerialName("user_id") val userId: String)

/**
 * Real, network-backed [RoomRepository]. Pairing happens entirely server-side
 * in the `matchmake` / `cancel_matchmaking` RPCs (`security definer` Postgres
 * functions - see `supabase/migrations/0002_matchmaking.sql`): the client can
 * neither create a room nor choose who it's paired with. Once paired, row
 * changes are streamed over a Realtime channel scoped to the room's id, and
 * each player tracks their presence on that same channel so the other side
 * can tell whether they're still connected.
 */
@Singleton
class SupabaseRoomRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) : RoomRepository {

    override suspend fun matchmake(gameId: GameId): Result<Room?> = try {
        val rows = supabaseClient.postgrest
            .rpc("matchmake", params(gameId))
            .decodeList<RoomRow>()
        Result.success(rows.firstOrNull()?.toDomain())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "matchmake failed", e)
        Result.failure(RoomException(friendlyError(e)))
    }

    override suspend fun cancelMatchmaking(gameId: GameId): Result<Unit> = try {
        supabaseClient.postgrest.rpc("cancel_matchmaking", params(gameId))
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "cancel_matchmaking failed", e)
        Result.failure(RoomException(friendlyError(e)))
    }

    override suspend fun connect(room: Room, selfUserId: String): RoomConnection {
        val channel = supabaseClient.channel("room-${room.id}")
        val connection = SupabaseRoomConnectionImpl(supabaseClient, channel, room.id)
        connection.start(selfUserId, initial = room)
        return connection
    }

    private fun params(gameId: GameId) =
        Json.encodeToJsonElement(MatchmakeParams(gameId = gameId.name)).jsonObject

    private fun friendlyError(e: Exception): String =
        if (e.message.orEmpty().contains("not authenticated", ignoreCase = true)) {
            "Sign in to play online"
        } else {
            "Couldn't reach the matchmaking server - check your connection"
        }
}

private class SupabaseRoomConnectionImpl(
    private val supabaseClient: SupabaseClient,
    private val channel: RealtimeChannel,
    private val roomId: String
) : RoomConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenerJob: Job? = null

    private val _roomUpdates = MutableStateFlow<Room?>(null)
    override val roomUpdates: Flow<Room> = _roomUpdates.filterNotNull()

    override val presentUserIds: Flow<Set<String>> =
        channel.presenceDataFlow<PresenceData>().map { list -> list.map { it.userId }.toSet() }

    suspend fun start(selfUserId: String, initial: Room) {
        _roomUpdates.value = initial

        val changeFlow = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = TABLE
            filter("id", FilterOperator.EQ, roomId)
        }
        listenerJob = scope.launch {
            changeFlow.collect { action ->
                runCatching { action.decodeRecord<RoomRow>() }
                    .onSuccess { _roomUpdates.value = it.toDomain() }
                    .onFailure { Log.w(TAG, "Failed to decode room update", it) }
            }
        }

        channel.subscribe(blockUntilSubscribed = true)
        runCatching { channel.track(PresenceData(userId = selfUserId)) }
    }

    override suspend fun submitMove(
        board: List<List<Int>>,
        nextTurn: String?,
        status: RoomStatus,
        winnerId: String?
    ): Result<Unit> = try {
        supabaseClient.from(TABLE).update(
            RoomMoveUpdate(
                boardState = board,
                currentTurn = nextTurn,
                status = status.name.lowercase(),
                winnerId = winnerId
            )
        ) {
            filter { eq("id", roomId) }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Failed to submit move for room $roomId", e)
        Result.failure(e)
    }

    override suspend fun markAbandoned(): Result<Unit> = try {
        supabaseClient.from(TABLE).update(RoomAbandonUpdate()) {
            filter { eq("id", roomId) }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Failed to mark room $roomId abandoned", e)
        Result.failure(e)
    }

    override suspend fun close() {
        listenerJob?.cancel()
        runCatching { channel.untrack() }
        runCatching { supabaseClient.realtime.removeChannel(channel) }
        scope.cancel()
    }
}
