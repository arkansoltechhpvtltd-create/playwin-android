package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.chess.ChessEngine
import com.example.playwin.core.domain.chess.ChessFen
import com.example.playwin.core.domain.chess.ChessPosition
import com.example.playwin.core.domain.chess.PieceColor
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The engine, not the screen, rules on a resignation: the side that resigns loses, whatever the board. */
class ChessResignResolveTest {

    private val engine = MockGameEngineRepository()

    private fun fen(text: String): ChessPosition = requireNotNull(ChessFen.fromFen(text))

    /** The result for the side that moved last, as the engine reports it. */
    private fun resolve(position: ChessPosition, resigned: PieceColor) =
        runBlocking { engine.resolve(GameId.CHESS, GameSnapshot.Chess(position, resigned = resigned)) }

    @Test
    fun `the side to move resigning is a win for the side that just moved`() {
        // Black just moved; White is to move and resigns.
        val position = fen("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2")
        assertEquals(GameOutcome.WIN, resolve(position, resigned = PieceColor.WHITE))
    }

    @Test
    fun `the side that just moved resigning loses`() {
        val position = fen("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2")
        assertEquals(GameOutcome.LOSE, resolve(position, resigned = PieceColor.BLACK))
    }

    @Test
    fun `resigning loses even when the other side couldn't mate`() {
        // White resigns; Black has only a king, which would be a draw on time - but not on resignation.
        val position = fen("4k3/8/8/8/8/8/8/4K2R w - - 0 1")
        assertEquals(GameOutcome.WIN, resolve(position, resigned = PieceColor.WHITE))
    }

    @Test
    fun `resigning before Black's reply in the opening still loses`() {
        val position = ChessEngine.initialPosition()
        // White to move, so Black "moved last"; White resigning is Black's win.
        assertEquals(GameOutcome.WIN, resolve(position, resigned = PieceColor.WHITE))
    }
}
