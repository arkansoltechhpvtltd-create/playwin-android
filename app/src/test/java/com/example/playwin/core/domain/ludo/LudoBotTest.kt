package com.example.playwin.core.domain.ludo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1v1 Red (computer, to move) vs Yellow. Global track indices: Red's local
 * position == global; Yellow's global == (local + 26) % 52. Safe globals are
 * 0, 8, 13, 21, 26, 34, 39, 47.
 */
class LudoBotTest {

    private fun state(red: List<Int>, yellow: List<Int> = List(4) { LudoEngine.IN_BASE }, dice: Int) = LudoState(
        tokens = red.mapIndexed { i, p -> Token(LudoColor.RED, i, p) } +
            yellow.mapIndexed { i, p -> Token(LudoColor.YELLOW, i, p) },
        currentPlayerIndex = 0,
        diceValue = dice,
        hasRolled = true,
        players = LudoEngine.TWO_PLAYER
    )

    private val base = LudoEngine.IN_BASE

    @Test
    fun `brings a token home over any other move`() {
        val s = state(red = listOf(53, 10, base, base), dice = 3)
        assertEquals(0, LudoBot.chooseToken(s)?.index)
    }

    @Test
    fun `captures an opponent when it can`() {
        // Yellow local 31 == global 5; Red token 0 at 2 + 3 lands on it.
        val s = state(red = listOf(2, 20, base, base), yellow = listOf(31, base, base, base), dice = 3)
        assertEquals(0, LudoBot.chooseToken(s)?.index)
    }

    @Test
    fun `gets a token out of base on a six`() {
        val s = state(red = listOf(base, 30, base, base), dice = 6)
        assertEquals(0, LudoBot.chooseToken(s)?.index)
    }

    @Test
    fun `does not step into striking range of an opponent`() {
        // Yellow local 42 == global 16. Red token 0: 14 -> 18 would sit 2 ahead of it.
        val s = state(red = listOf(14, 29, base, base), yellow = listOf(42, base, base, base), dice = 4)
        assertEquals(1, LudoBot.chooseToken(s)?.index)
    }

    @Test
    fun `runs a threatened token out of danger`() {
        // Yellow local 43 == global 17, 3 behind Red token 0 at global 20.
        val s = state(red = listOf(20, 30, base, base), yellow = listOf(43, base, base, base), dice = 5)
        assertEquals(0, LudoBot.chooseToken(s)?.index)
    }

    @Test
    fun `returns null when nothing can move`() {
        val s = state(red = listOf(base, base, base, base), dice = 3)
        assertNull(LudoBot.chooseToken(s))
    }

    @Test
    fun `a token on a safe square is never threatened`() {
        // Red at global 8 (star square) with Yellow 3 behind at global 5 (local 31).
        val s = state(red = listOf(8, base, base, base), yellow = listOf(31, base, base, base), dice = 1)
        assertFalse(LudoBot.isThreatened(s, s.tokens.first { it.color == LudoColor.RED && it.index == 0 }))

        val exposed = state(red = listOf(9, base, base, base), yellow = listOf(31, base, base, base), dice = 1)
        assertTrue(LudoBot.isThreatened(exposed, exposed.tokens.first { it.color == LudoColor.RED && it.index == 0 }))
    }

    @Test
    fun `two-player turns alternate between just the two colors`() {
        val s = state(red = listOf(10, base, base, base), dice = 3)
        val after = LudoEngine.applyMove(s, LudoColor.RED, 0)
        assertEquals(LudoColor.YELLOW, after.currentColor)
        assertEquals(LudoColor.RED, LudoEngine.forcePassTurn(after).currentColor)
    }
}
