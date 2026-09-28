package com.example.playwin.core.domain.ludo

/**
 * Pure-Kotlin Ludo rules: the shared 52-cell path, four private 6-cell home
 * columns, dice rolls, the need for a 6 to leave base, an extra roll on a 6,
 * capturing an opponent on a non-safe cell, and exact-count finishing. No
 * Android dependency - exercised directly by unit tests.
 */
object LudoEngine {
    const val SHARED_PATH_LENGTH = 52
    const val PLAYER_PATH_LENGTH = 51
    const val HOME_COLUMN_LENGTH = 6
    const val FINISH_LOCAL_POSITION = PLAYER_PATH_LENGTH - 1 + HOME_COLUMN_LENGTH // 56
    const val TOKENS_PER_COLOR = 4
    const val IN_BASE = -1

    val PLAYER_ORDER = listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)

    /** The classic 1v1 setup: opposite corners, Red moves first. Used by vs-Computer and online play. */
    val TWO_PLAYER = listOf(LudoColor.RED, LudoColor.YELLOW)

    /** The 4 entry squares plus the 4 "star" squares 8 cells after each entry - the classic 8 safe cells. */
    val SAFE_GLOBAL_INDICES: Set<Int> = PLAYER_ORDER.flatMap { color ->
        val start = startIndexFor(color)
        listOf(start, (start + 8) % SHARED_PATH_LENGTH)
    }.toSet()

    fun startIndexFor(color: LudoColor): Int = PLAYER_ORDER.indexOf(color) * 13

    fun globalIndexOf(color: LudoColor, localPosition: Int): Int {
        require(localPosition in 0 until PLAYER_PATH_LENGTH)
        return (startIndexFor(color) + localPosition) % SHARED_PATH_LENGTH
    }

    fun boardCellFor(color: LudoColor, localPosition: Int): BoardCell =
        if (localPosition < PLAYER_PATH_LENGTH) {
            BoardCell.Shared(globalIndexOf(color, localPosition))
        } else {
            BoardCell.HomeColumn(color, localPosition - PLAYER_PATH_LENGTH)
        }

    fun initialState(players: List<LudoColor> = PLAYER_ORDER): LudoState = LudoState(
        tokens = players.flatMap { color -> (0 until TOKENS_PER_COLOR).map { Token(color, it, IN_BASE) } },
        currentPlayerIndex = 0,
        players = players
    )

    fun rollDice(state: LudoState): LudoState = state.copy(diceValue = (1..6).random(), hasRolled = true)

    fun canMove(token: Token, dice: Int): Boolean = when {
        token.position == IN_BASE -> dice == 6
        else -> token.position + dice <= FINISH_LOCAL_POSITION
    }

    fun movableTokens(state: LudoState): List<Token> {
        val dice = state.diceValue ?: return emptyList()
        val color = state.currentColor
        return state.tokens.filter { it.color == color && canMove(it, dice) }
    }

    /** Applies a move (the token must be one of [movableTokens] for this state) and returns the resulting state. */
    fun applyMove(state: LudoState, color: LudoColor, tokenIndex: Int): LudoState {
        val dice = requireNotNull(state.diceValue) { "No dice rolled" }
        val token = state.tokens.first { it.color == color && it.index == tokenIndex }
        val newPosition = if (token.position == IN_BASE) 0 else token.position + dice

        var newTokens = state.tokens.map { if (it === token) it.copy(position = newPosition) else it }

        if (newPosition < PLAYER_PATH_LENGTH) {
            val landedGlobal = globalIndexOf(color, newPosition)
            if (landedGlobal !in SAFE_GLOBAL_INDICES) {
                newTokens = newTokens.map { other ->
                    val onSharedPath = other.position in 0 until PLAYER_PATH_LENGTH
                    if (other.color != color && onSharedPath && globalIndexOf(other.color, other.position) == landedGlobal) {
                        other.copy(position = IN_BASE)
                    } else {
                        other
                    }
                }
            }
        }

        val nextPlayerIndex = if (dice == 6) state.currentPlayerIndex else (state.currentPlayerIndex + 1) % state.players.size
        return state.copy(tokens = newTokens, diceValue = null, hasRolled = false, currentPlayerIndex = nextPlayerIndex)
    }

    /** Called when a roll leaves the current player with no legal move at all. */
    fun passTurn(state: LudoState): LudoState {
        val nextPlayerIndex = if (state.diceValue == 6) state.currentPlayerIndex else (state.currentPlayerIndex + 1) % state.players.size
        return state.copy(diceValue = null, hasRolled = false, currentPlayerIndex = nextPlayerIndex)
    }

    /** Forces the turn to the next player without a roll - used when the per-turn timer expires. */
    fun forcePassTurn(state: LudoState): LudoState = state.copy(
        diceValue = null,
        hasRolled = false,
        currentPlayerIndex = (state.currentPlayerIndex + 1) % state.players.size
    )

    fun hasAllTokensHome(positions: List<Int>): Boolean =
        positions.size == TOKENS_PER_COLOR && positions.all { it == FINISH_LOCAL_POSITION }
}
