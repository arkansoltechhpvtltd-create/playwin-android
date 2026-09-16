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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SupabaseGames"
private const val TABLE = "games"

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
    GameId.BINGO to ("Mark & call" to GameIconKey.BINGO),
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
 * Fails soft: an unreachable table, an empty result, or a row whose `id`
 * doesn't match a known [GameId] never crashes or blanks the lobby - this
 * falls back to [MockGamesRepository]'s built-in list instead.
 */
@Singleton
class SupabaseGamesRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val mockGamesRepository: MockGamesRepository
) : GamesRepository {

    override fun observeGames(): Flow<List<Game>> = flow {
        try {
            val rows = supabaseClient.from(TABLE)
                .select {
                    order("sort_order", Order.ASCENDING)
                }
                .decodeList<GameRow>()
            val games = rows.mapNotNull { it.toDomain() }
            if (games.isEmpty()) {
                Log.w(TAG, "Games table returned no mappable rows - falling back to the built-in catalogue")
                emitAll(mockGamesRepository.observeGames())
            } else {
                emit(games)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline or a query error: never leave the lobby empty.
            Log.w(TAG, "Failed to load games catalogue - falling back to the built-in list", e)
            emitAll(mockGamesRepository.observeGames())
        }
    }
}
