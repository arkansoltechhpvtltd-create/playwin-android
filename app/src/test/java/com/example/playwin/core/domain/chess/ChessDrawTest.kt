package com.example.playwin.core.domain.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChessDrawTest {

    private fun fen(text: String) = requireNotNull(ChessFen.fromFen(text))

    @Test
    fun `bare kings, or a lone minor piece, can't mate`() {
        assertEquals(GameStatus.INSUFFICIENT_MATERIAL, ChessEngine.gameStatus(fen("4k3/8/8/8/8/8/8/4K3 w - - 0 1")))
        assertEquals(GameStatus.INSUFFICIENT_MATERIAL, ChessEngine.gameStatus(fen("4k3/8/8/8/8/8/8/2N1K3 w - - 0 1")))
        assertEquals(GameStatus.INSUFFICIENT_MATERIAL, ChessEngine.gameStatus(fen("4k3/8/8/8/8/8/8/2B1K3 b - - 0 1")))
    }

    @Test
    fun `bishops all on one colour can't mate, on both colours they can`() {
        // c1 and f8 are both dark squares; c1 and c8 aren't.
        assertEquals(GameStatus.INSUFFICIENT_MATERIAL, ChessEngine.gameStatus(fen("4kb2/8/8/8/8/8/8/2B1K3 w - - 0 1")))
        assertEquals(GameStatus.IN_PROGRESS, ChessEngine.gameStatus(fen("2b1k3/8/8/8/8/8/8/2B1K3 w - - 0 1")))
    }

    @Test
    fun `a rook, a pawn, or two knights is still a game`() {
        assertEquals(GameStatus.IN_PROGRESS, ChessEngine.gameStatus(fen("4k3/8/8/8/8/8/8/R3K3 w - - 0 1")))
        assertEquals(GameStatus.IN_PROGRESS, ChessEngine.gameStatus(fen("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1")))
        assertEquals(GameStatus.IN_PROGRESS, ChessEngine.gameStatus(fen("4k3/8/8/8/8/8/8/1NN1K3 w - - 0 1")))
    }

    @Test
    fun `the clock counts quiet moves and resets on a pawn move or capture`() {
        val start = fen("4k3/8/8/8/8/8/4P3/R3K3 w - - 7 1")
        val rookMove = ChessEngine.applyMove(start, ChessMove(Square(0, 0), Square(0, 1)))
        assertEquals(8, rookMove.halfmoveClock)
        val pawnMove = ChessEngine.applyMove(start, ChessMove(Square(1, 4), Square(2, 4)))
        assertEquals(0, pawnMove.halfmoveClock)
        val capture = ChessEngine.applyMove(fen("r3k3/8/8/8/8/8/8/R3K3 w - - 30 1"), ChessMove(Square(0, 0), Square(7, 0)))
        assertEquals(0, capture.halfmoveClock)
    }

    @Test
    fun `fifty moves each without a capture or pawn move is a draw`() {
        assertEquals(GameStatus.IN_PROGRESS, ChessEngine.gameStatus(fen("4k3/8/8/8/8/8/8/R3K3 w - - 99 1")))
        assertEquals(GameStatus.FIFTY_MOVE_RULE, ChessEngine.gameStatus(fen("4k3/8/8/8/8/8/8/R3K3 w - - 100 1")))
    }

    @Test
    fun `checkmate on the hundredth half-move still wins`() {
        assertEquals(GameStatus.CHECKMATE, ChessEngine.gameStatus(fen("R5k1/5ppp/8/8/8/8/8/6K1 b - - 100 1")))
    }

    @Test
    fun `the clock survives a FEN round trip`() {
        val position = fen("4k3/8/8/8/8/8/8/R3K3 b - - 42 1")
        assertEquals(42, position.halfmoveClock)
        assertEquals(position, ChessFen.fromFen(ChessFen.toFen(position)))
    }

    // --- Threefold repetition ---

    private val knightsOut = listOf(
        ChessMove(Square(0, 6), Square(2, 5)), // Ng1-f3
        ChessMove(Square(7, 6), Square(5, 5)), // Ng8-f6
        ChessMove(Square(2, 5), Square(0, 6)), // Nf3-g1
        ChessMove(Square(5, 5), Square(7, 6))  // Nf6-g8
    )

    /** Plays [moves] from the start, returning the final position and the repetition keys of all earlier ones. */
    private fun play(moves: List<ChessMove>): Pair<ChessPosition, List<String>> {
        var position = ChessEngine.initialPosition()
        val earlier = mutableListOf<String>()
        for (move in moves) {
            earlier += ChessEngine.repetitionKey(position)
            position = ChessEngine.applyMove(position, move)
        }
        return position to earlier
    }

    @Test
    fun `the same position a third time is a draw`() {
        val (twice, twiceHistory) = play(knightsOut)
        assertEquals("only the second time", GameStatus.IN_PROGRESS, ChessEngine.gameStatus(twice, twiceHistory))

        val (thrice, thriceHistory) = play(knightsOut + knightsOut)
        assertEquals(GameStatus.THREEFOLD_REPETITION, ChessEngine.gameStatus(thrice, thriceHistory))
        assertTrue(GameStatus.THREEFOLD_REPETITION.isDraw)
    }

    @Test
    fun `the same pieces with the other side to move is a different position`() {
        val whiteToMove = fen("4k3/8/8/8/8/8/8/R3K3 w - - 0 1")
        val blackToMove = fen("4k3/8/8/8/8/8/8/R3K3 b - - 0 1")
        assertNotEquals(ChessEngine.repetitionKey(whiteToMove), ChessEngine.repetitionKey(blackToMove))
        val history = List(2) { ChessEngine.repetitionKey(blackToMove) }
        assertEquals(GameStatus.IN_PROGRESS, ChessEngine.gameStatus(whiteToMove, history))
    }

    @Test
    fun `losing a castling right makes it a different position`() {
        val rookOut = listOf(
            ChessMove(Square(0, 6), Square(2, 5)), // Ng1-f3
            ChessMove(Square(7, 6), Square(5, 5)), // Ng8-f6
            ChessMove(Square(0, 7), Square(0, 6)), // Rh1-g1: White loses kingside castling
            ChessMove(Square(5, 5), Square(7, 6)), // Nf6-g8
            ChessMove(Square(0, 6), Square(0, 7)), // Rg1-h1
            ChessMove(Square(7, 6), Square(5, 5))  // Ng8-f6
        )
        // Same pieces as after move 2, but White can no longer castle kingside: not a repeat of it.
        val (position, history) = play(rookOut + rookOut.drop(2).take(4))
        assertNotEquals(GameStatus.THREEFOLD_REPETITION, ChessEngine.gameStatus(position, history))
    }

    @Test
    fun `an en passant square only counts when the capture is really possible`() {
        val afterE4 = ChessEngine.applyMove(ChessEngine.initialPosition(), ChessMove(Square(1, 4), Square(3, 4)))
        // No black pawn can take on e3, so the position is the same as without the en passant square.
        assertEquals(ChessEngine.repetitionKey(afterE4.copy(enPassantTarget = null)), ChessEngine.repetitionKey(afterE4))

        val canCapture = fen("4k3/8/8/8/3pP3/8/8/4K3 b - e3 0 1")
        assertNotEquals(ChessEngine.repetitionKey(canCapture.copy(enPassantTarget = null)), ChessEngine.repetitionKey(canCapture))
    }

    @Test
    fun `computer against computer always reaches an end`() {
        repeat(4) { seed ->
            val random = Random(seed)
            var position = ChessEngine.initialPosition()
            var plies = 0
            while (ChessEngine.gameStatus(position) == GameStatus.IN_PROGRESS) {
                position = ChessEngine.applyMove(position, requireNotNull(ChessBot.chooseMove(position, random)))
                plies++
                // 16 pawns x 6 steps and 30 captures bound a game under the fifty-move rule.
                assertTrue("seed $seed still going after $plies plies", plies < 6000)
            }
            assertNotEquals(GameStatus.IN_PROGRESS, ChessEngine.gameStatus(position))
        }
    }
}
