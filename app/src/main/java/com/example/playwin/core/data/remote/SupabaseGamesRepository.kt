package com.example.playwin.core.data.remote

import android.util.Log
import com.example.playwin.core.data.mock.MockGamesRepository
import com.example.playwin.core.domain.model.Game
import com.example.playwin.core.domain.model.GameIconKey
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.repository.GamesRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SupabaseGames"
private const val TABLE = "games"
private const val CACHE_PREFS = "games_catalogue"
private const val CACHE_KEY = "rows"

/** How long the lobby waits on the network before showing the saved or built-in list instead. */
private const val FETCH_TIMEOUT_MILLIS = 4_000L

/**
 * Tagline and icon aren't stored in the `games` table (it's just id/name/
 * playable/sort_order) - they're presentation-only and never change, so they
 * stay in this local lookup keyed by the same [GameId] the table's `id`
 * column must match.
 */
private val PRESENTATION: Map<GameId, Pair<String, GameIconKey>> = mapOf(
    GameId.TWENTY_FORTY_EIGHT to ("Merge to win" to GameIconKey.GRID),
    GameId.EIGHT_BALL to ("Pocket them all" to GameIconKey.BILLIARDS),
    GameId.CARROM to ("Strike & pot" to GameIconKey.CARROM),
    GameId.CONNECT_FOUR to ("4 in a row wins" to GameIconKey.FOUR_IN_A_ROW),
    GameId.SUDOKU to ("Fill the grid" to GameIconKey.SUDOKU),
    GameId.WORD_HUNT to ("Find the words" to GameIconKey.WORD_HUNT),
    GameId.CHESS to ("Outsmart them" to GameIconKey.CHESS),
    GameId.LUDO to ("Race home first" to GameIconKey.LUDO),
    GameId.RUMMY to ("Build your sets" to GameIconKey.CARDS)
)

@Serializable
private data class GameRow(
    val id: String,
    val name: String,
    val playable: Boolean,
    @SerialName("sort_order") val sortOrder: Int
) {
    /** Null when [id] doesn't match a known [GameId] - the row is skipped, not crashed on. */
    fun toDomain(): Game? {
        val gameId = runCatching { GameId.valueOf(id) }.getOrNull()
        if (gameId == null) {
            Log.w(TAG, "Games table row has unrecognized id '$id' - expected one of ${GameId.entries.map { it.name }}")
            return null
        }
        val (tagline, icon) = PRESENTATION.getValue(gameId)
        return Game(
            id = gameId,
            title = name,
            tagline = tagline,
            icon = icon,
            isFullyPlayable = playable
        )
    }
}

/**
 * Real, network-backed [GamesRepository] - the lobby catalogue lives in the
 * `games` table (public read, no auth required) ordered by `sort_order`.
 *
 * The catalogue barely ever changes, so it is loaded once per process rather
 * than once per lobby visit, and the last good copy is kept on disk: a repeat
 * launch shows it straight away and refreshes behind it. The load runs in this
 * repository's own scope, so it survives the lobby's ViewModel being recreated
 * and can be started early (see [observeGames]'s first collector).
 *
 * Fails soft: an unreachable or slow table, an empty result, or a row whose
 * `id` doesn't match a known [GameId] never crashes or blanks the lobby - this
 * falls back to the saved copy, or to [MockGamesRepository]'s built-in list.
 */
@Singleton
class SupabaseGamesRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val mockGamesRepository: MockGamesRepository,
    @ApplicationContext context: Context
) : GamesRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** Null until the first answer - from disk, the network or the fallback - is in. */
    private val games = MutableStateFlow<List<Game>?>(null)
    private val loadLock = Any()
    private var loadJob: Job? = null
    @Volatile private var loadedFromNetwork = false

    override fun observeGames(): Flow<List<Game>> = flow {
        ensureLoading()
        emitAll(games.filterNotNull())
    }

    /** Starts the one load, or a fresh attempt if the last one never reached the network. */
    private fun ensureLoading() {
        synchronized(loadLock) {
            if (loadedFromNetwork || loadJob?.isActive == true) return
            loadJob = scope.launch { load() }
        }
    }

    private suspend fun load() {
        if (games.value == null) {
            readCache()?.let { games.value = it }
        }
        try {
            val rows = withTimeout(FETCH_TIMEOUT_MILLIS) {
                supabaseClient.from(TABLE)
                    .select {
                        order("sort_order", Order.ASCENDING)
                    }
                    .decodeList<GameRow>()
            }
            val fetched = rows.mapNotNull { it.toDomain() }
            if (fetched.isEmpty()) {
                Log.w(TAG, "Games table returned no mappable rows - falling back to the built-in catalogue")
            } else {
                games.value = fetched
                loadedFromNetwork = true
                prefs.edit().putString(CACHE_KEY, json.encodeToString(rows)).apply()
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Games catalogue took longer than ${FETCH_TIMEOUT_MILLIS}ms - using the fallback")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline or a query error: never leave the lobby empty.
            Log.w(TAG, "Failed to load games catalogue - using the fallback", e)
        }
        if (games.value == null) {
            games.value = mockGamesRepository.observeGames().first()
        }
    }

    private fun readCache(): List<Game>? = runCatching {
        prefs.getString(CACHE_KEY, null)
            ?.let { json.decodeFromString<List<GameRow>>(it) }
            ?.mapNotNull { it.toDomain() }
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
