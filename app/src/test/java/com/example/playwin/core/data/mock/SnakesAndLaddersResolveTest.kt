package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SnakesAndLaddersResolveTest {

    private val engine = MockGameEngineRepository()

    private fun resolve(before: SnakesAndLaddersState, roll: Int) = runBlocking {
        engine.resolve(GameId.SNAKES_AND_LADDERS, GameSnapshot.SnakesAndLadders(before, roll))
    }

    private fun at(mine: Int, theirs: Int = 0) = SnakesAndLaddersState(positions = listOf(mine, theirs))

    @Test
    fun `an ordinary roll is still in progress`() {
        assertEquals(GameOutcome.IN_PROGRESS, resolve(at(10), 3))
        assertEquals(GameOutcome.IN_PROGRESS, resolve(at(85), 2)) // snake to 24
    }

    @Test
    fun `reaching or passing 100 wins`() {
        assertEquals(GameOutcome.WIN, resolve(at(96), 4))
        assertEquals(GameOutcome.WIN, resolve(at(98), 6))
    }

    @Test
    fun `a ladder that tops out short of 100 doesn't win`() {
        assertEquals(GameOutcome.IN_PROGRESS, resolve(at(70), 1)) // 71 -> 91
    }

    @Test
    fun `a fabricated roll or position loses`() {
        assertEquals(GameOutcome.LOSE, resolve(at(96), 0))
        assertEquals(GameOutcome.LOSE, resolve(at(96), 7))
        assertEquals(GameOutcome.LOSE, resolve(at(101), 1))
        assertEquals(GameOutcome.LOSE, resolve(SnakesAndLaddersState(positions = listOf(5)), 1))
    }

    @Test
    fun `a game that was already won can't be won again`() {
        assertEquals(GameOutcome.LOSE, resolve(at(mine = 40, theirs = 100), 2))
    }

    @Test
    fun `the die only ever shows 1 to 6, and every face comes up`() = runBlocking {
        val seeded = MockGameEngineRepository(Random(7))
        val rolls = List(600) { seeded.rollDie(GameId.SNAKES_AND_LADDERS) }
        assertTrue(rolls.all { it in 1..6 })
        assertEquals((1..6).toSet(), rolls.toSet())
    }
}
