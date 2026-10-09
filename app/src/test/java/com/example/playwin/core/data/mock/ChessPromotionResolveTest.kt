package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.chess.ChessEngine
import com.example.playwin.core.domain.chess.ChessFen
import com.example.playwin.core.domain.chess.PieceType
import com.example.playwin.core.domain.chess.Square
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The result seam sees the promoted piece: a promotion that mates is a win for the promoter. */
class ChessPromotionResolveTest {

    private val engine = MockGameEngineRepository()

    // a7-a8 with Black's king boxed in on h8 by its own pawns.
    private val position = requireNotNull(ChessFen.fromFen("7k/P5pp/8/8/8/8/8/K7 w - - 0 1"))

    private fun resolveAfterPromotingTo(piece: PieceType): GameOutcome {
        val move = requireNotNull(ChessEngine.legalMove(position, Square(6, 0), Square(7, 0), piece))
        return runBlocking { engine.resolve(GameId.CHESS, GameSnapshot.Chess(ChessEngine.applyMove(position, move))) }
    }

    @Test
    fun `promoting to a mating queen wins`() {
        assertEquals(GameOutcome.WIN, resolveAfterPromotingTo(PieceType.QUEEN))
    }

    @Test
    fun `promoting to a piece that doesn't mate plays on`() {
        assertEquals(GameOutcome.IN_PROGRESS, resolveAfterPromotingTo(PieceType.KNIGHT))
        assertEquals(GameOutcome.IN_PROGRESS, resolveAfterPromotingTo(PieceType.BISHOP))
    }
}
