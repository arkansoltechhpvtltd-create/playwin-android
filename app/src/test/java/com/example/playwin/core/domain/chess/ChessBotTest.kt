package com.example.playwin.core.domain.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The computer opponent's choices. Unless a test says otherwise, every difficulty must pass. */
class ChessBotTest {

    private fun fen(text: String) = requireNotNull(ChessFen.fromFen(text))
    private fun sq(name: String) = Square(name[1].digitToInt() - 1, name[0] - 'a')

    /** Runs [check] on the move each difficulty picks, with a few seeds for the ones that vary. */
    private fun eachDifficulty(position: ChessPosition, check: (ChessDifficulty, ChessMove) -> Unit) {
        ChessDifficulty.entries.forEach { difficulty ->
            val seeds = if (difficulty.randomMarginCp > 0) 0 until 8 else 0 until 1
            for (seed in seeds) check(difficulty, assertNotNullMove(ChessBot.chooseMove(position, difficulty, random = Random(seed))))
        }
    }

    /** Whether the side to move in [position] has a mate in one. */
    private fun hasMateInOne(position: ChessPosition) = ChessEngine.legalMoves(position).any {
        ChessEngine.gameStatus(ChessEngine.applyMove(position, it)) == GameStatus.CHECKMATE
    }

    @Test
    fun `takes a free hanging piece`() {
        // The black knight on d5 is undefended: Rd1xd5.
        eachDifficulty(fen("4k3/8/8/3n4/8/8/8/3RK3 w - - 0 1")) { difficulty, move ->
            assertEquals("$difficulty", sq("d5"), move.to)
        }
        // Playing Black: the white knight on d5 is undefended: Rd8xd5.
        eachDifficulty(fen("3rk3/8/8/3N4/8/8/8/4K3 b - - 0 1")) { difficulty, move ->
            assertEquals("$difficulty", sq("d5"), move.to)
        }
    }

    @Test
    fun `plays mate in one`() {
        // Back-rank mate: Ra1-a8#.
        eachDifficulty(fen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1")) { difficulty, move ->
            assertEquals("$difficulty", sq("a1"), move.from)
            assertEquals("$difficulty", sq("a8"), move.to)
        }
        // And as Black, with a free knight on c8 as a distraction: ...Ra8-a1#.
        val black = fen("r1N3k1/8/8/8/8/8/5PPP/6K1 b - - 0 1")
        eachDifficulty(black) { difficulty, move ->
            assertEquals("$difficulty", GameStatus.CHECKMATE, ChessEngine.gameStatus(ChessEngine.applyMove(black, move)))
        }
    }

    @Test
    fun `does not leave its own piece hanging when a safe move exists`() {
        // The pawn on e5 attacks the white knight on d4; every knight move is safe.
        val position = fen("4k3/8/8/4p3/3N4/8/8/4K3 w - - 0 1")
        eachDifficulty(position) { difficulty, move ->
            assertEquals("$difficulty", sq("d4"), move.from)
            val after = ChessEngine.applyMove(position, move)
            assertTrue("$difficulty left the knight en prise", ChessEngine.legalMoves(after).none { it.to == move.to })
        }
    }

    @Test
    fun `moves its queen out of attack rather than leaving it hanging`() {
        // Black's knight on c6 attacks the white queen on d4 (undefended).
        eachDifficulty(fen("4k3/8/2n5/8/3Q4/8/8/4K3 w - - 0 1")) { difficulty, move ->
            assertTrue("$difficulty", move.from == sq("d4") || move.to == sq("c6"))
        }
    }

    @Test
    fun `won't trade its queen for a defended pawn`() {
        // Qd1xd5 wins a pawn but e6xd5 wins the queen back.
        eachDifficulty(fen("4k3/8/4p3/3p4/8/8/8/3QK3 w - - 0 1")) { difficulty, move ->
            assertNotEquals("$difficulty", sq("d5"), move.to)
        }
    }

    @Test
    fun `sees through an exchange - won't start a capture sequence that loses material`() {
        // Rxd5 takes a pawn defended by the c6 pawn and the d8 rook; after cxd5 Rxd5 Rxd5 White is a rook down.
        eachDifficulty(fen("3rk3/8/2p5/3p4/8/8/8/3RK3 w - - 0 1")) { difficulty, move ->
            assertNotEquals("$difficulty", sq("d5"), move.to)
        }
    }

    @Test
    fun `stops a mate in one against it`() {
        // White threatens Ra1-a8#; Black must make luft, or guard the back rank.
        val position = fen("6k1/5ppp/8/8/8/8/5PPP/R5K1 b - - 0 1")
        eachDifficulty(position) { difficulty, move ->
            assertTrue("$difficulty allowed mate", !hasMateInOne(ChessEngine.applyMove(position, move)))
        }
    }

    @Test
    fun `medium and hard find a mate in two`() {
        // Kc6-b6! leaves Black only Ka8-b8, then Rh1-h8#.
        val position = fen("k7/8/2K5/8/8/8/8/7R w - - 0 1")
        listOf(ChessDifficulty.MEDIUM, ChessDifficulty.HARD).forEach { difficulty ->
            val move = assertNotNullMove(ChessBot.chooseMove(position, difficulty, random = Random(1)))
            val after = ChessEngine.applyMove(position, move)
            val replies = ChessEngine.legalMoves(after)
            assertTrue("$difficulty", replies.isNotEmpty())
            replies.forEach { reply -> assertTrue("$difficulty: $move then $reply", hasMateInOne(ChessEngine.applyMove(after, reply))) }
        }
    }

    @Test
    fun `promotes to a queen when that's best`() {
        eachDifficulty(fen("8/P7/8/7k/8/8/8/4K3 w - - 0 1")) { difficulty, move ->
            assertEquals("$difficulty", sq("a8"), move.to)
            assertEquals("$difficulty", PieceType.QUEEN, move.promotion)
        }
    }

    @Test
    fun `has no move when checkmated`() {
        val mated = fen("R5k1/5ppp/8/8/8/8/8/6K1 b - - 0 1")
        assertEquals(GameStatus.CHECKMATE, ChessEngine.gameStatus(mated))
        ChessDifficulty.entries.forEach { assertNull(ChessBot.chooseMove(mated, it)) }
    }

    @Test
    fun `only ever plays legal moves`() {
        listOf(ChessDifficulty.EASY to 40, ChessDifficulty.HARD to 12).forEach { (difficulty, plies) ->
            var position = ChessEngine.initialPosition()
            repeat(plies) { ply ->
                if (ChessEngine.gameStatus(position) != GameStatus.IN_PROGRESS) return@forEach
                val move = assertNotNullMove(ChessBot.chooseMove(position, difficulty, random = Random(ply), timeBudgetMillis = 200))
                assertTrue("$difficulty", move in ChessEngine.legalMoves(position))
                position = ChessEngine.applyMove(position, move)
            }
        }
    }

    @Test
    fun `answers within its time budget`() {
        val middlegame = fen("r1bq1rk1/pp2bppp/2n1pn2/3p4/2PP4/2N1PN2/PP3PPP/R2QKB1R w KQ - 0 8")
        ChessDifficulty.entries.forEach { difficulty ->
            val start = System.nanoTime()
            assertNotNullMove(ChessBot.chooseMove(middlegame, difficulty))
            val millis = (System.nanoTime() - start) / 1_000_000
            assertTrue("$difficulty took $millis ms", millis <= difficulty.timeBudgetMillis + 500)
        }
    }

    private fun assertNotNullMove(move: ChessMove?): ChessMove {
        assertNotNull(move)
        return move!!
    }
}
