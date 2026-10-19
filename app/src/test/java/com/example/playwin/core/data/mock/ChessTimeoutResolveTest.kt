package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.chess.ChessFen
import com.example.playwin.core.domain.chess.ChessPosition
import com.example.playwin.core.domain.chess.PieceColor
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The engine, not the screen, rules on a flag: a loss on time, or a draw when the other side couldn't mate. */
class ChessTimeoutResolveTest {

    private val engine = MockGameEngineRepository()

    private fun fen(text: String): ChessPosition = requireNotNull(ChessFen.fromFen(text))

    /** The result for the side that moved last, as the engine reports it. */
    private fun resolve(position: ChessPosition, timedOut: PieceColor) =
        runBlocking { engine.resolve(GameId.CHESS, GameSnapshot.Chess(position, timedOut = timedOut)) }

    @Test
    fun `the side to move running out of time loses - a win for the side that just moved`() {
        // Black just moved; White is to move and flags.
        val position = fen("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2")
        assertEquals(GameOutcome.WIN, resolve(position, timedOut = PieceColor.WHITE))
    }

    @Test
    fun `the side that just moved running out of time loses`() {
        val position = fen("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2")
        assertEquals(GameOutcome.LOSE, resolve(position, timedOut = PieceColor.BLACK))
    }

    @Test
    fun `a flag against a side that can't mate is a draw`() {
        // White flags, but Black has only a king left.
        val position = fen("4k3/8/8/8/8/8/8/4K2R w - - 0 1")
        assertEquals(GameOutcome.DRAW, resolve(position, timedOut = PieceColor.WHITE))
    }
}
