package com.example.playwin.core.domain.chinesecheckers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChineseCheckersBotTest {

    /** Plays the bot against itself; returns the winner and how many moves it took. */
    private fun selfPlay(first: ChineseCheckersDifficulty, second: ChineseCheckersDifficulty, seed: Int, maxPlies: Int = 400): Pair<Int?, Int> {
        val random = Random(seed)
        var position = ChineseCheckersRules.initialPosition()
        repeat(maxPlies) { ply ->
            val mover = position.sideToMove
            val difficulty = if (mover == 1) first else second
            val move = ChineseCheckersBot.chooseMove(position, difficulty, random)
            position = if (move == null) ChineseCheckersRules.pass(position) else ChineseCheckersRules.play(position, move)
            if (ChineseCheckersRules.hasWon(position, mover)) return mover to ply + 1
        }
        return null to maxPlies
    }

    @Test
    fun `medium against medium always finishes - nobody gets stuck`() {
        repeat(6) { seed ->
            val (winner, plies) = selfPlay(ChineseCheckersDifficulty.MEDIUM, ChineseCheckersDifficulty.MEDIUM, seed)
            assertNotNull("game $seed never finished", winner)
            assertTrue("game $seed took $plies moves", plies < 300)
        }
    }

    @Test
    fun `easy against easy always finishes too`() {
        repeat(6) { seed ->
            val (winner, _) = selfPlay(ChineseCheckersDifficulty.EASY, ChineseCheckersDifficulty.EASY, seed)
            assertNotNull("game $seed never finished", winner)
        }
    }

    @Test
    fun `medium beats easy most of the time`() {
        var mediumWins = 0
        repeat(10) { seed ->
            // Alternate who goes first.
            if (seed % 2 == 0) {
                if (selfPlay(ChineseCheckersDifficulty.MEDIUM, ChineseCheckersDifficulty.EASY, seed).first == 1) mediumWins++
            } else {
                if (selfPlay(ChineseCheckersDifficulty.EASY, ChineseCheckersDifficulty.MEDIUM, seed).first == 2) mediumWins++
            }
        }
        assertTrue("medium won $mediumWins of 10", mediumWins >= 7)
    }

    @Test
    fun `the bot takes a winning move when it has one`() {
        // Player 2 has nine pegs in its goal and the tenth one step from the last hole.
        val goal = ChineseCheckersBoard.goal(2)
        val cells = MutableList(ChineseCheckersBoard.holes.size) { 0 }
        val open = goal.first { hole -> ChineseCheckersBoard.neighbours[hole].any { it >= 0 && it !in goal } }
        goal.filter { it != open }.forEach { cells[it] = 2 }
        val outside = ChineseCheckersBoard.neighbours[open].first { it >= 0 && it !in goal }
        cells[outside] = 2
        val move = ChineseCheckersBot.chooseMove(ChineseCheckersPosition(cells, sideToMove = 2), ChineseCheckersDifficulty.EASY, Random(1))
        assertEquals(open, move?.to)
    }

    @Test
    fun `the opening move goes forward and every move is legal`() {
        val start = ChineseCheckersRules.initialPosition()
        val move = ChineseCheckersBot.chooseMove(start, ChineseCheckersDifficulty.MEDIUM, Random(3))!!
        assertTrue(move in ChineseCheckersRules.legalMoves(start))
        assertTrue("moved backwards", ChineseCheckersBoard.holes[move.to].r < ChineseCheckersBoard.holes[move.from].r)
    }

    @Test
    fun `a medium move is quick to find, even mid-game`() {
        var position = ChineseCheckersRules.initialPosition()
        val random = Random(9)
        var slowest = 0L
        repeat(60) {
            val start = System.nanoTime()
            val move = ChineseCheckersBot.chooseMove(position, ChineseCheckersDifficulty.MEDIUM, random) ?: return@repeat
            slowest = maxOf(slowest, (System.nanoTime() - start) / 1_000_000)
            position = ChineseCheckersRules.play(position, move)
        }
        assertTrue("slowest move took ${slowest}ms", slowest < 500)
    }
}
