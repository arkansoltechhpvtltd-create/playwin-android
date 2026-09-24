package com.example.playwin.core.data.remote

import android.util.Log
import com.example.playwin.core.domain.connectfour.Connect4Logic
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SupabaseRoom"
private const val TABLE = "rooms"
private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O or 1/I - easy to read aloud
private const val CODE_LENGTH = 6
private const val MAX_CREATE_ATTEMPTS = 5

/** Thrown for expected room-pairing failures so screens can show their message. */
class RoomException(message: String) : Exception(message)

private fun generateRoomCode(): String = buildString {
    repeat(CODE_LENGTH) { append(CODE_CHARS.random()) }
}

@Serializable
private data class RoomInsertRow(
    val id: String,
    val code: String,
    @SerialName("game_id") val gameId: String = "CONNECT_FOUR",
    @SerialName("host_id") val hostId: String,
    val status: String = "waiting",
    @SerialName("board_state") val boardState: List<List<Int>>
)

@Serializable
private data class JoinRoomParams(@SerialName("p_code") val code: String)

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
    val code: String,
    @SerialName("host_id") val hostId: String,
    @SerialName("guest_id") val guestId: String? = null,
    val status: String,
    @SerialName("current_turn") val currentTurn: String? = null,
    @SerialName("board_state") val boardState: List<List<Int>> = emptyList(),
    @SerialName("winner_id") val winnerId: String? = null
) {
    fun toDomain(): Room = Room(
        id = id,
        code = code,
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
 * Real, network-backed [RoomRepository]. Room creation is a plain insert;
 * joining goes through the `join_room` RPC (a `security definer` Postgres
 * function - see `supabase/migrations/0001_rooms.sql`) so a client never
 * needs generic UPDATE access to a room it hasn't joined yet. Row changes are
 * streamed over a Realtime channel scoped to the room's id, and each player
 * tracks their presence on that same channel so the other side can tell
 * whether they're still connected.
 */
@Singleton
class SupabaseRoomRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) : RoomRepository {

    override suspend fun createRoom(hostId: String): Result<Room> {
        var lastError: Exception? = null
        repeat(MAX_CREATE_ATTEMPTS) {
            val row = RoomInsertRow(
                id = UUID.randomUUID().toString(),
                code = generateRoomCode(),
                hostId = hostId,
                boardState = Connect4Logic.emptyBoard()
            )
            try {
                val inserted = supabaseClient.from(TABLE)
                    .insert(row) { select() }
                    .decodeSingle<RoomRow>()
                return Result.success(inserted.toDomain())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Most likely a room-code collision (unique constraint) - retry with a fresh code.
                lastError = e
                Log.w(TAG, "createRoom attempt failed, retrying", e)
            }
        }
        return Result.failure(lastError ?: RoomException("Could not create a room"))
    }

    override suspend fun joinRoom(userId: String, code: String): Result<Room> = try {
        val params = Json.encodeToJsonElement(JoinRoomParams(code = code.trim().uppercase())).jsonObject
        val result = supabaseClient.postgrest
            .rpc("join_room", params)
            .decodeAs<RoomRow>()
        Result.success(result.toDomain())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Failed to join room with code $code", e)
        Result.failure(RoomException(friendlyJoinError(e)))
    }

    override suspend fun connect(room: Room, selfUserId: String): RoomConnection {
        val channel = supabaseClient.channel("room-${room.id}")
        val connection = SupabaseRoomConnectionImpl(supabaseClient, channel, room.id)
        connection.start(selfUserId, initial = room)
        return connection
    }

    private fun friendlyJoinError(e: Exception): String {
        val message = e.message.orEmpty()
        return when {
            message.contains("own room", ignoreCase = true) -> "You can't join your own room"
            message.contains("not authenticated", ignoreCase = true) -> "Sign in to play online"
            else -> "Room not found or already full - check the code and try again"
        }
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
