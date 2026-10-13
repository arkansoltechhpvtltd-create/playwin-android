package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The engine rules a carrom board won by the first to clear their coins, once the queen is covered. */
class CarromResolveTest {

    private val engine = MockGameEngineRepository()

    private fun resolve(shooterLeft: Int, opponentLeft: Int, queenSettled: Boolean = true, shooterScore: Int = 0, opponentScore: Int = 0) =
        runBlocking {
            engine.resolve(GameId.CARROM, GameSnapshot.Carrom(shooterLeft, opponentLeft, queenSettled, shooterScore, opponentScore))
        }

    @Test
    fun `clearing your coins wins, whatever the points`() {
        assertEquals(GameOutcome.WIN, resolve(shooterLeft = 0, opponentLeft = 5, shooterScore = 4, opponentScore = 9))
    }

    @Test
    fun `clearing the opponent's coins loses`() {
        assertEquals(GameOutcome.LOSE, resolve(shooterLeft = 3, opponentLeft = 0))
    }

    @Test
    fun `clearing both sides at once loses - the opponent's last coin went too`() {
        assertEquals(GameOutcome.LOSE, resolve(shooterLeft = 0, opponentLeft = 0))
    }

    @Test
    fun `nothing is decided before the queen is covered`() {
        assertEquals(GameOutcome.IN_PROGRESS, resolve(shooterLeft = 0, opponentLeft = 4, queenSettled = false))
    }

    @Test
    fun `coins left on both sides plays on`() {
        assertEquals(GameOutcome.IN_PROGRESS, resolve(shooterLeft = 2, opponentLeft = 4))
    }
}
