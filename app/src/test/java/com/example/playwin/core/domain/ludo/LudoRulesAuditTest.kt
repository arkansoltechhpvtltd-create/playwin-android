package com.example.playwin.core.domain.ludo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules checked in the rules audit that the main engine tests don't already pin down. */
class LudoRulesAuditTest {

    private val start = LudoEngine.initialState(LudoEngine.TWO_PLAYER)

    @Test
    fun `only a real die face can be rolled`() {
        assertThrows(IllegalArgumentException::class.java) { LudoEngine.rollDice(start, 0) }
        assertThrows(IllegalArgumentException::class.java) { LudoEngine.rollDice(start, 7) }
        (1..6).forEach { assertEquals(it, LudoEngine.rollDice(start, it).diceValue) }
    }

    @Test
    fun `a token in its home column can never be captured`() {
        // Yellow sits in its own home column; Red lands on the shared cell with the same
        // index Yellow's column would have if it were on the track - nothing happens to it.
        val tokens = start.tokens.map {
            when {
                it.color == LudoColor.RED && it.index == 0 -> it.copy(position = 10)
                it.color == LudoColor.YELLOW && it.index == 0 -> it.copy(position = LudoEngine.PLAYER_PATH_LENGTH + 2)
                else -> it
            }
        }
        val rolled = LudoEngine.rollDice(start.copy(tokens = tokens), 4)
        val after = LudoEngine.applyMove(rolled, LudoColor.RED, 0)
        assertEquals(LudoEngine.PLAYER_PATH_LENGTH + 2, after.tokens.first { it.color == LudoColor.YELLOW && it.index == 0 }.position)
    }

    @Test
    fun `a released token comes out onto its safe entry square`() {
        val rolled = LudoEngine.rollDice(start, 6)
        val after = LudoEngine.applyMove(rolled, LudoColor.RED, 0)
        val token = after.tokens.first { it.color == LudoColor.RED && it.index == 0 }
        assertEquals(0, token.position)
        assertTrue(LudoEngine.globalIndexOf(LudoColor.RED, 0) in LudoEngine.SAFE_GLOBAL_INDICES)
        // And the 6 earns another roll.
        assertEquals(LudoColor.RED, after.currentColor)
    }

    @Test
    fun `three sixes in a row forfeit even with a token that could move`() {
        val out = start.tokens.map { if (it.color == LudoColor.RED && it.index == 0) it.copy(position = 5) else it }
        val third = LudoEngine.rollDice(start.copy(tokens = out, sixesInARow = 2), 6)
        assertTrue(LudoEngine.isTurnForfeited(third))
        assertTrue(LudoEngine.movableTokens(third).isEmpty())
        assertEquals(LudoColor.YELLOW, LudoEngine.passTurn(third).currentColor)
    }
}
