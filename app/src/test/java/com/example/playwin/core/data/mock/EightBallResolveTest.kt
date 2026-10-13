package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The engine's ruling on the 8-ball, for vs-Computer and online alike. */
class EightBallResolveTest {

    private val engine = MockGameEngineRepository()

    private fun resolve(snapshot: GameSnapshot.EightBall) = runBlocking { engine.resolve(GameId.EIGHT_BALL, snapshot) }

    @Test
    fun `a clean 8 after clearing the group wins`() {
        assertEquals(GameOutcome.WIN, resolve(GameSnapshot.EightBall(shooterClearedGroup = true, eightBallPocketed = true, scratched = false)))
    }

    @Test
    fun `an 8 on a foul loses, even with the group cleared`() {
        val fouled = GameSnapshot.EightBall(shooterClearedGroup = true, eightBallPocketed = true, scratched = false, fouled = true)
        assertEquals(GameOutcome.LOSE, resolve(fouled))
    }

    @Test
    fun `an 8 on the break plays on`() {
        val onBreak = GameSnapshot.EightBall(shooterClearedGroup = false, eightBallPocketed = true, scratched = false, isBreakShot = true)
        assertEquals(GameOutcome.IN_PROGRESS, resolve(onBreak))
    }
}
