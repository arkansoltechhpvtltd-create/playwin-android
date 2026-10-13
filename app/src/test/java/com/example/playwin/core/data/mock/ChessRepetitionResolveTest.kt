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

/** The engine rules a threefold repetition a draw from the game's history - the same for vs-Computer and online. */
class ChessRepetitionResolveTest {

    private val engine = MockGameEngineRepository()

    private val knightsOutAndBack = listOf(
        ChessMove(Square(0, 6), Square(2, 5)),
        ChessMove(Square(7, 6), Square(5, 5)),
        ChessMove(Square(2, 5), Square(0, 6)),
        ChessMove(Square(5, 5), Square(7, 6))
    )

    private fun resolveAfter(moves: List<ChessMove>): GameOutcome {
        var position: ChessPosition = ChessEngine.initialPosition()
        val earlier = mutableListOf<String>()
        for (move in moves) {
            earlier += ChessEngine.repetitionKey(position)
            position = ChessEngine.applyMove(position, move)
        }
        return runBlocking { engine.resolve(GameId.CHESS, GameSnapshot.Chess(position, earlier)) }
    }

    @Test
    fun `a position repeated twice plays on`() {
        assertEquals(GameOutcome.IN_PROGRESS, resolveAfter(knightsOutAndBack))
    }

    @Test
    fun `a position repeated three times is a draw`() {
        assertEquals(GameOutcome.DRAW, resolveAfter(knightsOutAndBack + knightsOutAndBack))
    }
}
