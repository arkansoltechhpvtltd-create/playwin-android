package com.example.playwin.feature.game.ludo.online

import com.example.playwin.core.domain.ludo.LudoColor
import com.example.playwin.core.domain.ludo.LudoEngine
import com.example.playwin.core.domain.ludo.LudoState
import com.example.playwin.core.domain.ludo.Token
import com.example.playwin.core.domain.model.Room
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Online Ludo is 1v1: the room's host (player 1) is Red and moves first, the
 * guest (player 2) is Yellow. This is the shape stored in `rooms.board_state`.
 * A fresh room holds `{}`, which decodes to the standard starting position.
 */
@Serializable
private data class LudoRoomState(
    val red: List<Int>,
    val yellow: List<Int>,
    /** Index into [LudoEngine.TWO_PLAYER] of whose turn it is. */
    val turn: Int,
    /** The current, not-yet-used roll, if the player to move has rolled. */
    val dice: Int? = null,
    /** The most recent roll by either player, so the other side can see what was rolled even when it was a pass. */
    val lastRoll: Int? = null,
    /** Sixes the player to move has rolled back to back this turn; a third forfeits the turn. */
    val sixes: Int = 0
)

private val json = Json { ignoreUnknownKeys = true }

fun ludoPlayerNumber(color: LudoColor): Int = LudoEngine.TWO_PLAYER.indexOf(color) + 1

fun ludoColorOf(playerNumber: Int): LudoColor = LudoEngine.TWO_PLAYER[playerNumber - 1]

/** The room's Ludo position, or the starting position for a fresh (or unreadable) room. */
fun Room.ludoState(): LudoState {
    val stored = runCatching { json.decodeFromJsonElement<LudoRoomState>(state) }.getOrNull()
        ?: return LudoEngine.initialState(LudoEngine.TWO_PLAYER)
    fun tokens(color: LudoColor, positions: List<Int>) = positions.mapIndexed { i, p -> Token(color, i, p) }
    return LudoState(
        tokens = tokens(LudoColor.RED, stored.red) + tokens(LudoColor.YELLOW, stored.yellow),
        currentPlayerIndex = stored.turn,
        diceValue = stored.dice,
        hasRolled = stored.dice != null,
        players = LudoEngine.TWO_PLAYER,
        sixesInARow = stored.sixes
    )
}

/** The most recent roll recorded in the room, for display. */
fun Room.ludoLastRoll(): Int? = runCatching { json.decodeFromJsonElement<LudoRoomState>(state) }.getOrNull()?.lastRoll

fun LudoState.toRoomJson(lastRoll: Int?): JsonElement {
    fun positions(color: LudoColor) = tokens.filter { it.color == color }.sortedBy { it.index }.map { it.position }
    return json.encodeToJsonElement(
        LudoRoomState(
            red = positions(LudoColor.RED),
            yellow = positions(LudoColor.YELLOW),
            turn = currentPlayerIndex,
            dice = diceValue,
            lastRoll = lastRoll,
            sixes = sixesInARow
        )
    )
}
