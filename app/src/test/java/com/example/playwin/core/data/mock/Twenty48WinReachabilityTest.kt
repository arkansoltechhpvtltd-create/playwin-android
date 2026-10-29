package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.twenty48.SwipeDirection
import com.example.playwin.core.domain.twenty48.Twenty48Rules
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 2048 must be winnable for real: merging two 1024s makes the 2048 tile and the
 * engine calls it a win at once, and a reasonable player actually gets there in
 * normal play - with the same slide, merge and spawn rules the game screen uses.
 */
class Twenty48WinReachabilityTest {

    private val engine = MockGameEngineRepository()

    private suspend fun verdict(board: List<List<Int>>) =
        engine.resolve(GameId.TWENTY_FORTY_EIGHT, GameSnapshot.TwentyFortyEight(board))

    @Test
    fun `merging two 1024s makes 2048 and wins`() = runTest {
        val empty = listOf(0, 0, 0, 0)
        val before = listOf(listOf(1024, 1024, 0, 0), empty, empty, empty)
        // One move away is not a win yet.
        assertEquals(GameOutcome.IN_PROGRESS, verdict(before))

        val (merged, moved) = Twenty48Rules.move(before, SwipeDirection.LEFT)
        assertTrue(moved)
        assertEquals(listOf(2048, 0, 0, 0), merged[0])
        // Won straight off the merge, and still after the spawn the screen adds.
        assertEquals(GameOutcome.WIN, verdict(merged))
        assertEquals(GameOutcome.WIN, verdict(Twenty48Rules.spawnRandomTile(merged, Random(1))))

        // Same in every direction, along a column too.
        val column = listOf(listOf(0, 1024, 0, 0), listOf(0, 1024, 0, 0), empty, empty)
        for (direction in SwipeDirection.entries) {
            val start = if (direction == SwipeDirection.LEFT || direction == SwipeDirection.RIGHT) before else column
            val (after, _) = Twenty48Rules.move(start, direction)
            assertEquals(direction.name, 1, after.flatten().count { it == 2048 })
            assertEquals(direction.name, GameOutcome.WIN, verdict(after))
        }
    }

    @Test
    fun `an auto-player reaches 2048 in normal play and wins on that very move`() = runTest {
        val games = 20
        var wins = 0
        val maxTiles = mutableListOf<Int>()
        repeat(games) { seed ->
            val random = Random(seed)
            var board = Twenty48Rules.emptyBoardWithStartingTiles(random)
            var won = false
            while (true) {
                val direction = bestMove(board) ?: break
                val (moved, changed) = Twenty48Rules.move(board, direction)
                check(changed)
                board = Twenty48Rules.spawnRandomTile(moved, random)
                val outcome = verdict(board)
                if (board.flatten().any { it >= Twenty48Rules.WIN_TILE }) {
                    // The first board holding 2048 wins - never earlier, never missed.
                    assertEquals(GameOutcome.WIN, outcome)
                    won = true
                    break
                }
                assertTrue("no win without the tile (seed $seed)", outcome != GameOutcome.WIN)
                if (outcome == GameOutcome.LOSE) {
                    // Only ever a genuinely stuck board.
                    assertTrue(!Twenty48Rules.hasMovesLeft(board))
                    break
                }
            }
            if (won) wins++
            maxTiles += board.flatten().max()
        }
        println("2048 auto-play: $wins/$games games reached 2048; max tiles $maxTiles")
        assertTrue("no game reached 2048 - max tiles $maxTiles", wins > 0)
    }

    // --- A simple expectimax player: one move, then the chance of each spawn, then its best reply. ---

    /** Snake-shaped weights pulling the big tiles into the top-left corner in order. */
    private val weights = listOf(
        listOf(65536.0, 32768.0, 16384.0, 8192.0),
        listOf(512.0, 1024.0, 2048.0, 4096.0),
        listOf(256.0, 128.0, 64.0, 32.0),
        listOf(2.0, 4.0, 8.0, 16.0)
    )

    private fun heuristic(board: List<List<Int>>): Double {
        var score = 0.0
        var empties = 0
        for (r in 0 until 4) for (c in 0 until 4) {
            score += board[r][c] * weights[r][c]
            if (board[r][c] == 0) empties++
        }
        return score * (1 + empties * 0.1)
    }

    private fun bestMove(board: List<List<Int>>): SwipeDirection? =
        SwipeDirection.entries
            .mapNotNull { direction ->
                val (after, moved) = Twenty48Rules.move(board, direction)
                if (moved) direction to expectedAfterSpawn(after) else null
            }
            .maxByOrNull { it.second }?.first

    private fun expectedAfterSpawn(board: List<List<Int>>): Double {
        val emptyCells = (0 until 4).flatMap { r -> (0 until 4).map { c -> r to c } }.filter { (r, c) -> board[r][c] == 0 }
        if (emptyCells.isEmpty()) return heuristic(board)
        var total = 0.0
        for ((r, c) in emptyCells) {
            for ((tile, chance) in listOf(2 to 0.9, 4 to 0.1)) {
                val spawned = board.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, v -> if (j == c) tile else v } else row }
                val reply = SwipeDirection.entries.maxOfOrNull { direction ->
                    val (after, moved) = Twenty48Rules.move(spawned, direction)
                    if (moved) heuristic(after) else Double.NEGATIVE_INFINITY
                } ?: Double.NEGATIVE_INFINITY
                total += chance * (if (reply == Double.NEGATIVE_INFINITY) 0.0 else reply)
            }
        }
        return total / emptyCells.size
    }
}
