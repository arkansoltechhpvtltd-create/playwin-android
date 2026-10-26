package com.example.playwin.feature.game.snakesandladders.online

import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersMove
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersRules
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Online Snakes and Ladders is 1v1: the room's host (player 1) is player
 * index 0 and rolls first, the guest (player 2) is index 1. This is the shape
 * stored in `rooms.board_state`. Only the server writes it - the
 * `roll_and_move` function in
 * `supabase/migrations/0009_snakes_and_ladders_server_dice.sql` - so the app
 * only ever reads it. A fresh room holds `{}`, which decodes to the starting
 * position.
 */
@Serializable
private data class SnakesAndLaddersRoomState(
    val positions: List<Int>,
    /** Index of the player to roll next. */
    val turn: Int,
    val sixes: Int = 0,
    val turns: Int = 0,
    /** The roll that produced this position, so both players replay exactly the same move. */
    val last: LastRoll? = null
)

@Serializable
private data class LastRoll(
    val player: Int,
    val roll: Int,
    val from: Int,
    val forfeited: Boolean = false
)

private val json = Json { ignoreUnknownKeys = true }

private fun Room.stored(): SnakesAndLaddersRoomState? =
    runCatching { json.decodeFromJsonElement<SnakesAndLaddersRoomState>(state) }.getOrNull()
        ?.takeIf { it.positions.size == SnakesAndLaddersRules.PLAYERS && it.turn in it.positions.indices }

fun snakesAndLaddersPlayerIndex(playerNumber: Int): Int = playerNumber - 1

fun snakesAndLaddersPlayerNumber(playerIndex: Int): Int = playerIndex + 1

/** The room's game, or the starting position for a fresh (or unreadable) room. */
fun Room.snakesAndLaddersState(): SnakesAndLaddersState {
    val stored = stored() ?: return SnakesAndLaddersState()
    return SnakesAndLaddersState(
        positions = stored.positions,
        turn = stored.turn,
        sixesInARow = stored.sixes,
        turnsPlayed = stored.turns
    )
}

/** The roll that produced the room's position, re-derived from the rules rather than read back as a result. */
fun Room.snakesAndLaddersLastMove(): SnakesAndLaddersMove? {
    val last = stored()?.last ?: return null
    if (last.roll !in SnakesAndLaddersRules.DIE_FACES) return null
    return if (last.forfeited) {
        SnakesAndLaddersMove(last.player, last.roll, last.from, last.from, last.from, forfeited = true)
    } else {
        SnakesAndLaddersRules.move(last.player, last.from, last.roll)
    }
}
