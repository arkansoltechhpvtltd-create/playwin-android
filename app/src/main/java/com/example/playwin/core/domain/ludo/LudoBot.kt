package com.example.playwin.core.domain.ludo

/**
 * The "Play vs Computer" Ludo opponent. Given a rolled state, it scores each
 * legal token move and picks the best:
 *  - bringing a token home beats everything,
 *  - then capturing an opponent,
 *  - then getting a token out of base,
 *  - then escaping a token that's currently within striking range of an
 *    opponent, and landing somewhere safe (a safe square or the home column),
 * with a penalty for landing within striking range, and progress along the
 * track as the tie-breaker. Simple, but it won't blunder tokens for free.
 */
object LudoBot {

    /** Returns the token to move for the state's current player, or null if there's no legal move. */
    fun chooseToken(state: LudoState): Token? =
        LudoEngine.movableTokens(state).maxByOrNull { score(state, it) }

    internal fun score(state: LudoState, token: Token): Double {
        val after = LudoEngine.applyMove(state, token.color, token.index)
        val moved = after.tokens.first { it.color == token.color && it.index == token.index }
        var score = moved.position / 10.0

        if (moved.position == LudoEngine.FINISH_LOCAL_POSITION) score += 100
        if (capturedCount(state, after, token.color) > 0) score += 80
        if (token.position == LudoEngine.IN_BASE) score += 50

        val wasThreatened = isThreatened(state, token)
        val nowThreatened = isThreatened(after, moved)
        if (wasThreatened && !nowThreatened) score += 25
        if (nowThreatened) score -= 40
        if (!nowThreatened && isSafe(moved)) score += 15

        return score
    }

    private fun capturedCount(before: LudoState, after: LudoState, mover: LudoColor): Int {
        fun inBase(s: LudoState) = s.tokens.count { it.color != mover && it.position == LudoEngine.IN_BASE }
        return inBase(after) - inBase(before)
    }

    private fun isSafe(token: Token): Boolean =
        token.position >= LudoEngine.PLAYER_PATH_LENGTH ||
            (token.position >= 0 && LudoEngine.globalIndexOf(token.color, token.position) in LudoEngine.SAFE_GLOBAL_INDICES)

    /** Whether some opponent token sits 1..6 squares behind [token] on the shared track and could land on it. */
    internal fun isThreatened(state: LudoState, token: Token): Boolean {
        if (token.position !in 0 until LudoEngine.PLAYER_PATH_LENGTH || isSafe(token)) return false
        val target = LudoEngine.globalIndexOf(token.color, token.position)
        return state.tokens.any { other ->
            if (other.color == token.color || other.position !in 0 until LudoEngine.PLAYER_PATH_LENGTH) return@any false
            val from = LudoEngine.globalIndexOf(other.color, other.position)
            val distance = (target - from + LudoEngine.SHARED_PATH_LENGTH) % LudoEngine.SHARED_PATH_LENGTH
            // It must also still be on its shared-track leg when it gets there, not turned into its home column.
            distance in 1..6 && other.position + distance < LudoEngine.PLAYER_PATH_LENGTH
        }
    }
}
