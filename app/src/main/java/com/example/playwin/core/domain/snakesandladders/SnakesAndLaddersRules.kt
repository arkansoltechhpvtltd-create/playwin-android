package com.example.playwin.core.domain.snakesandladders

import kotlin.math.min

/**
 * A two-player game in progress.
 *
 * @param positions each player's square, by player index; [SnakesAndLaddersRules.START] is
 * off the board, before square 1.
 * @param turn the index of the player to roll next.
 * @param sixesInARow sixes the player to roll has already thrown back to back this turn.
 * @param turnsPlayed rolls taken so far by either player, forfeited ones included.
 */
data class SnakesAndLaddersState(
    val positions: List<Int> = List(SnakesAndLaddersRules.PLAYERS) { SnakesAndLaddersRules.START },
    val turn: Int = 0,
    val sixesInARow: Int = 0,
    val turnsPlayed: Int = 0
)

/**
 * One roll as played: [player] threw [roll] standing on [from], walked to
 * [landed] (100 at most) and finished on [to] - a ladder's top or a snake's
 * tail when [landed] is one's foot or head. A [forfeited] roll doesn't move.
 */
data class SnakesAndLaddersMove(
    val player: Int,
    val roll: Int,
    val from: Int,
    val landed: Int,
    val to: Int,
    val forfeited: Boolean = false
) {
    val climbedLadder: Boolean get() = to > landed
    val hitSnake: Boolean get() = to < landed
}

/** The result of [SnakesAndLaddersRules.play]: the move made and the game after it. */
data class SnakesAndLaddersTurn(
    val move: SnakesAndLaddersMove,
    val after: SnakesAndLaddersState,
    /** The roll was a 6 (and not a forfeiting one): the same player rolls again. */
    val extraTurn: Boolean,
    /** The mover reached or passed square 100. */
    val won: Boolean
)

/**
 * Pure-Kotlin Snakes and Ladders rules: one die, move forward that many
 * squares, climb a ladder from its foot or slide down a snake from its head,
 * and roll again on a 6. Reaching *or passing* 100 wins - there's no exact
 * roll needed and no bouncing back. A run of [MAX_SIXES_IN_A_ROW] sixes
 * forfeits: that last roll doesn't move and the turn passes.
 *
 * The die itself is never thrown here or in the UI; this only applies a roll
 * it's handed. Against the computer the roll comes from
 * [com.example.playwin.core.domain.repository.GameEngineRepository.rollDie].
 * Online, the server rolls *and* moves (`roll_and_move` in
 * `supabase/migrations/0009_snakes_and_ladders_server_dice.sql`, which
 * mirrors these rules), and the app only uses [move] to replay that roll.
 */
object SnakesAndLaddersRules {
    const val PLAYERS = 2
    const val START = 0
    const val FINISH = SnakesAndLaddersBoard.LAST_SQUARE
    val DIE_FACES = 1..6

    /** Sixes in a row that forfeit the turn. Set it to [Int.MAX_VALUE] to never forfeit. */
    const val MAX_SIXES_IN_A_ROW = 3

    /** The move [player] makes from [from] with [roll], ignoring the sixes rule. */
    fun move(player: Int, from: Int, roll: Int): SnakesAndLaddersMove {
        val landed = min(from + roll, FINISH)
        return SnakesAndLaddersMove(player, roll, from, landed, SnakesAndLaddersBoard.destination(landed))
    }

    /** Applies [roll] for the player to move in [state], which must not already be won. */
    fun play(state: SnakesAndLaddersState, roll: Int): SnakesAndLaddersTurn {
        require(roll in DIE_FACES) { "Not a die roll: $roll" }
        require(winner(state) == null) { "The game is already over" }
        val player = state.turn
        val from = state.positions[player]
        val sixes = if (roll == 6) state.sixesInARow + 1 else 0
        val forfeited = sixes >= MAX_SIXES_IN_A_ROW

        val move = if (forfeited) {
            SnakesAndLaddersMove(player, roll, from, from, from, forfeited = true)
        } else {
            move(player, from, roll)
        }
        val won = move.to >= FINISH
        val extraTurn = roll == 6 && !forfeited && !won
        val after = state.copy(
            positions = state.positions.mapIndexed { i, p -> if (i == player) move.to else p },
            turn = if (extraTurn) player else (player + 1) % PLAYERS,
            sixesInARow = if (extraTurn) sixes else 0,
            turnsPlayed = state.turnsPlayed + 1
        )
        return SnakesAndLaddersTurn(move, after, extraTurn, won)
    }

    /** The index of the player who has reached 100, if either has. */
    fun winner(state: SnakesAndLaddersState): Int? =
        state.positions.indexOfFirst { it >= FINISH }.takeIf { it >= 0 }
}
