package com.example.playwin.core.domain.chess

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * [ChessSearch] keeps its own fast board and move generator for searching;
 * these perft counts (chessprogramming.org "Perft Results", the same as
 * [ChessPerftTest] checks the rules engine against) prove it generates
 * exactly the legal moves [ChessEngine] does - castling, en passant,
 * promotion, pins and check evasion included - and that make/unmake
 * restores every position.
 */
class ChessSearchPerftTest {

    private fun assertPerft(fen: String, vararg expected: Long) {
        val position = requireNotNull(ChessFen.fromFen(fen)) { "Bad FEN: $fen" }
        expected.forEachIndexed { i, count ->
            val search = ChessSearch(position, emptyList(), ChessDifficulty.MEDIUM, Random(0))
            assertEquals("$fen at depth ${i + 1}", count, search.perft(i + 1))
        }
    }

    @Test
    fun `start position`() = assertPerft("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 20, 400, 8902, 197281)

    @Test
    fun `kiwipete - castling, en passant and pins`() =
        assertPerft("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 48, 2039, 97862, 4085603)

    @Test
    fun `position 3 - en passant discovered checks`() =
        assertPerft("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2812, 43238, 674624)

    @Test
    fun `position 4 - promotions and castling under attack`() =
        assertPerft("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 6, 264, 9467, 422333)

    @Test
    fun `position 5 - promotion with capture`() =
        assertPerft("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 44, 1486, 62379, 2103487)
}
