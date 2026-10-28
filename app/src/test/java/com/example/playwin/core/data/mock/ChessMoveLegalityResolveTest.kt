package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.chess.ChessEngine
import com.example.playwin.core.domain.chess.ChessMove
import com.example.playwin.core.domain.chess.ChessPosition
import com.example.playwin.core.domain.chess.Square
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The engine re-checks the move that led to a position, so a mate can't be claimed with a made-up one. */
class ChessMoveLegalityResolveTest {

    private val engine = MockGameEngineRepository()

    private fun resolve(snapshot: GameSnapshot.Chess) = runBlocking { engine.resolve(GameId.CHESS, snapshot) }

    /** Plays [moves] (from, to as (row, col) pairs) from the start, returning each position and the keys before it. */
    private fun play(vararg moves: Pair<Pair<Int, Int>, Pair<Int, Int>>): Triple<ChessPosition, List<String>, ChessMove> {
        var position = ChessEngine.initialPosition()
        val history = mutableListOf<String>()
        var last: ChessMove? = null
        moves.forEach { (from, to) ->
            val move = requireNotNull(ChessEngine.legalMove(position, Square(from.first, from.second), Square(to.first, to.second)))
            history += ChessEngine.repetitionKey(position)
            position = ChessEngine.applyMove(position, move)
            last = move
        }
        return Triple(position, history, last!!)
    }

    // Fool's mate: 1. f3 e5 2. g4 Qh4#.
    private val foolsMate = arrayOf((1 to 5) to (2 to 5), (6 to 4) to (4 to 4), (1 to 6) to (3 to 6), (7 to 3) to (3 to 7))

    @Test
    fun `a genuine mating move wins`() {
        val (mate, history, move) = play(*foolsMate)
        val (before, _, _) = play(*foolsMate.copyOfRange(0, 3))
        assertEquals(GameOutcome.WIN, resolve(GameSnapshot.Chess(mate, history, before = before, move = move)))
    }

    @Test
    fun `a mate reached by an illegal move loses`() {
        val (mate, history, _) = play(*foolsMate)
        val (before, _, _) = play(*foolsMate.copyOfRange(0, 3))
        // A knight's leap for the queen (d8-e6): not a legal queen move.
        val illegal = ChessMove(Square(7, 3), Square(5, 4))
        assertEquals(GameOutcome.LOSE, resolve(GameSnapshot.Chess(mate, history, before = before, move = illegal)))
    }

    @Test
    fun `a legal move that does not lead to the reported position loses`() {
        val (mate, history, _) = play(*foolsMate)
        val (before, _, _) = play(*foolsMate.copyOfRange(0, 3))
        // Legal from there, but it isn't the move that produced the mate on the board.
        val other = requireNotNull(ChessEngine.legalMove(before, Square(6, 0), Square(5, 0)))
        assertEquals(GameOutcome.LOSE, resolve(GameSnapshot.Chess(mate, history, before = before, move = other)))
    }

    @Test
    fun `a before-position that is not the last one in the history loses`() {
        val (mate, history, move) = play(*foolsMate)
        val (before, _, _) = play(*foolsMate.copyOfRange(0, 3))
        val shuffled = history.dropLast(1) + ChessEngine.repetitionKey(ChessEngine.initialPosition())
        assertEquals(GameOutcome.LOSE, resolve(GameSnapshot.Chess(mate, shuffled, before = before, move = move)))
    }

    @Test
    fun `a move sent without the position it came from loses`() {
        val (mate, history, move) = play(*foolsMate)
        assertEquals(GameOutcome.LOSE, resolve(GameSnapshot.Chess(mate, history, move = move)))
    }

    @Test
    fun `an ordinary legal move stays in progress`() {
        val (after, history, move) = play((1 to 4) to (3 to 4))
        assertEquals(GameOutcome.IN_PROGRESS, resolve(GameSnapshot.Chess(after, history, before = ChessEngine.initialPosition(), move = move)))
    }
}
