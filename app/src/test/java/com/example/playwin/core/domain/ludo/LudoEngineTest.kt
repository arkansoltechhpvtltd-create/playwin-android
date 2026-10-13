package com.example.playwin.core.domain.ludo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoEngineTest {

    private fun tokensWith(vararg overrides: Pair<Pair<LudoColor, Int>, Int>): List<Token> {
        val overrideMap = overrides.toMap()
        return LudoEngine.PLAYER_ORDER.flatMap { color ->
            (0 until LudoEngine.TOKENS_PER_COLOR).map { index ->
                Token(color, index, overrideMap[color to index] ?: LudoEngine.IN_BASE)
            }
        }
    }

    @Test
    fun `the main path has 52 unique cells`() {
        assertEquals(52, LudoEngine.SHARED_PATH_LENGTH)
        val allGlobalCells = (0 until LudoEngine.SHARED_PATH_LENGTH).toSet()
        assertEquals(52, allGlobalCells.size)

        // Each color's 51-step journey visits 51 distinct shared cells, all within range.
        for (color in LudoEngine.PLAYER_ORDER) {
            val visited = (0 until LudoEngine.PLAYER_PATH_LENGTH).map { LudoEngine.globalIndexOf(color, it) }
            assertEquals(LudoEngine.PLAYER_PATH_LENGTH, visited.toSet().size)
            assertTrue(visited.all { it in allGlobalCells })
        }
    }

    @Test
    fun `each colors home-entry maps to that colors own private home column`() {
        for (color in LudoEngine.PLAYER_ORDER) {
            val lastSharedCell = LudoEngine.boardCellFor(color, LudoEngine.PLAYER_PATH_LENGTH - 1)
            assertTrue(lastSharedCell is BoardCell.Shared)

            for (i in 0 until LudoEngine.HOME_COLUMN_LENGTH) {
                val cell = LudoEngine.boardCellFor(color, LudoEngine.PLAYER_PATH_LENGTH + i)
                assertEquals(BoardCell.HomeColumn(color, i), cell)
            }
        }
    }

    @Test
    fun `home-column cells never collide with the shared track`() {
        for (color in LudoEngine.PLAYER_ORDER) {
            for (i in 0 until LudoEngine.HOME_COLUMN_LENGTH) {
                val cell = LudoEngine.boardCellFor(color, LudoEngine.PLAYER_PATH_LENGTH + i)
                assertTrue(cell is BoardCell.HomeColumn)
            }
        }
        val homeCell: BoardCell = BoardCell.HomeColumn(LudoColor.RED, 0)
        val sharedCell: BoardCell = BoardCell.Shared(0)
        assertNotEquals(homeCell, sharedCell)
    }

    @Test
    fun `a token needs a six to leave base`() {
        val token = Token(LudoColor.RED, 0, LudoEngine.IN_BASE)
        assertFalse(LudoEngine.canMove(token, 5))
        assertTrue(LudoEngine.canMove(token, 6))
    }

    @Test
    fun `a token needs an exact roll to finish - overshoot is blocked`() {
        val nearFinish = Token(LudoColor.RED, 0, LudoEngine.FINISH_LOCAL_POSITION - 2)
        assertFalse(LudoEngine.canMove(nearFinish, 3))
        assertTrue(LudoEngine.canMove(nearFinish, 2))
    }

    @Test
    fun `landing on an opponent on a non-safe cell captures it`() {
        // RED token at local 1 rolling a 3 lands on global cell 4, which is not a safe square.
        // BLUE's local 17 also maps to global cell 4 ((39 + 17) % 52 == 4).
        val tokens = tokensWith((LudoColor.RED to 0) to 1, (LudoColor.BLUE to 0) to 17)
        val state = LudoState(tokens = tokens, currentPlayerIndex = 0, diceValue = 3)

        assertFalse(4 in LudoEngine.SAFE_GLOBAL_INDICES)
        val result = LudoEngine.applyMove(state, LudoColor.RED, 0)

        val movedRed = result.tokens.first { it.color == LudoColor.RED && it.index == 0 }
        val capturedBlue = result.tokens.first { it.color == LudoColor.BLUE && it.index == 0 }
        assertEquals(4, movedRed.position)
        assertEquals(LudoEngine.IN_BASE, capturedBlue.position)
    }

    @Test
    fun `landing on an opponent on a safe cell does not capture it`() {
        // RED local 5 rolling a 3 lands on global cell 8, a safe "star" square.
        // GREEN's local 47 also maps to global 8 ((13 + 47) % 52 == 8).
        val tokens = tokensWith((LudoColor.RED to 0) to 5, (LudoColor.GREEN to 0) to 47)
        val state = LudoState(tokens = tokens, currentPlayerIndex = 0, diceValue = 3)

        assertTrue(8 in LudoEngine.SAFE_GLOBAL_INDICES)
        val result = LudoEngine.applyMove(state, LudoColor.RED, 0)

        val green = result.tokens.first { it.color == LudoColor.GREEN && it.index == 0 }
        assertEquals(47, green.position) // untouched - safe squares block capture
    }

    @Test
    fun `rolling a six grants another turn`() {
        val tokens = tokensWith((LudoColor.RED to 0) to 0)
        val state = LudoState(tokens = tokens, currentPlayerIndex = 0, diceValue = 6)
        val result = LudoEngine.applyMove(state, LudoColor.RED, 0)
        assertEquals(0, result.currentPlayerIndex)
    }

    @Test
    fun `a non-six roll passes the turn to the next player`() {
        val tokens = tokensWith((LudoColor.RED to 0) to 0)
        val state = LudoState(tokens = tokens, currentPlayerIndex = 0, diceValue = 3)
        val result = LudoEngine.applyMove(state, LudoColor.RED, 0)
        assertEquals(1, result.currentPlayerIndex)
    }

    @Test
    fun `capturing an opponent grants another roll`() {
        // Same capture as above (RED local 1 + 3 -> global 4, onto BLUE), with a non-six roll.
        val tokens = tokensWith((LudoColor.RED to 0) to 1, (LudoColor.BLUE to 0) to 17)
        val state = LudoState(tokens = tokens, currentPlayerIndex = 0, diceValue = 3, hasRolled = true)
        val result = LudoEngine.applyMove(state, LudoColor.RED, 0)

        assertEquals(LudoEngine.IN_BASE, result.tokens.first { it.color == LudoColor.BLUE && it.index == 0 }.position)
        assertEquals(0, result.currentPlayerIndex)
        assertEquals(null, result.diceValue)
        assertFalse(result.hasRolled)
    }

    @Test
    fun `the computer also rolls again after a capture`() {
        // 1v1: YELLOW (the computer, index 1) local 1 + 3 lands on global 30 ((26 + 4) % 52), where RED's local 30 sits.
        val tokens = LudoEngine.initialState(LudoEngine.TWO_PLAYER).tokens.map {
            when {
                it.color == LudoColor.YELLOW && it.index == 0 -> it.copy(position = 1)
                it.color == LudoColor.RED && it.index == 0 -> it.copy(position = 30)
                else -> it
            }
        }
        val state = LudoState(tokens = tokens, currentPlayerIndex = 1, players = LudoEngine.TWO_PLAYER, diceValue = 3)

        assertFalse(30 in LudoEngine.SAFE_GLOBAL_INDICES)
        val result = LudoEngine.applyMove(state, LudoColor.YELLOW, 0)

        assertEquals(LudoEngine.IN_BASE, result.tokens.first { it.color == LudoColor.RED && it.index == 0 }.position)
        assertEquals(1, result.currentPlayerIndex)
    }

    @Test
    fun `moving a token home grants another roll`() {
        val tokens = tokensWith((LudoColor.RED to 0) to LudoEngine.FINISH_LOCAL_POSITION - 3)
        val state = LudoState(tokens = tokens, currentPlayerIndex = 0, diceValue = 3)
        val result = LudoEngine.applyMove(state, LudoColor.RED, 0)

        assertEquals(LudoEngine.FINISH_LOCAL_POSITION, result.tokens.first { it.color == LudoColor.RED && it.index == 0 }.position)
        assertEquals(0, result.currentPlayerIndex)
    }

    @Test
    fun `a move short of home without a capture still passes the turn`() {
        // Into the home column, but not to the finish.
        val tokens = tokensWith((LudoColor.RED to 0) to LudoEngine.FINISH_LOCAL_POSITION - 5)
        val state = LudoState(tokens = tokens, currentPlayerIndex = 0, diceValue = 3)
        assertEquals(1, LudoEngine.applyMove(state, LudoColor.RED, 0).currentPlayerIndex)
    }

    @Test
    fun `landing on an opponent on a safe cell does not grant another roll`() {
        val tokens = tokensWith((LudoColor.RED to 0) to 5, (LudoColor.GREEN to 0) to 47)
        val state = LudoState(tokens = tokens, currentPlayerIndex = 0, diceValue = 3)
        assertEquals(1, LudoEngine.applyMove(state, LudoColor.RED, 0).currentPlayerIndex)
    }

    @Test
    fun `a six that also captures still grants just one roll`() {
        // RED local 1 + 6 -> global 7 (not safe); BLUE local 20 -> (39 + 20) % 52 == 7.
        val tokens = tokensWith((LudoColor.RED to 0) to 1, (LudoColor.BLUE to 0) to 20)
        val state = LudoState(tokens = tokens, currentPlayerIndex = 0, diceValue = 6, hasRolled = true)

        assertFalse(7 in LudoEngine.SAFE_GLOBAL_INDICES)
        val result = LudoEngine.applyMove(state, LudoColor.RED, 0)

        assertEquals(LudoEngine.IN_BASE, result.tokens.first { it.color == LudoColor.BLUE && it.index == 0 }.position)
        // One fresh roll for the same player: back to an un-rolled state, nothing banked.
        assertEquals(0, result.currentPlayerIndex)
        assertEquals(null, result.diceValue)
        assertFalse(result.hasRolled)
    }

    @Test
    fun `a player wins only when all four tokens have reached the finish position`() {
        val allHome = List(4) { LudoEngine.FINISH_LOCAL_POSITION }
        val notAllHome = List(3) { LudoEngine.FINISH_LOCAL_POSITION } + listOf(10)
        assertTrue(LudoEngine.hasAllTokensHome(allHome))
        assertFalse(LudoEngine.hasAllTokensHome(notAllHome))
    }

    // --- Three sixes in a row ---

    /** Red rolls [value] and, if it can, moves token 0. */
    private fun rollAndMove(state: LudoState, value: Int): LudoState {
        val rolled = LudoEngine.rollDice(state, value)
        return if (LudoEngine.movableTokens(rolled).isEmpty()) LudoEngine.passTurn(rolled) else LudoEngine.applyMove(rolled, LudoColor.RED, 0)
    }

    @Test
    fun `two sixes in a row still earn another roll`() {
        var state = LudoState(tokens = tokensWith((LudoColor.RED to 0) to 0), currentPlayerIndex = 0)
        state = rollAndMove(state, 6)
        state = rollAndMove(state, 6)
        assertEquals(0, state.currentPlayerIndex)
        assertEquals(2, state.sixesInARow)
    }

    @Test
    fun `a third six in a row is void and passes the turn`() {
        var state = LudoState(tokens = tokensWith((LudoColor.RED to 0) to 0), currentPlayerIndex = 0)
        state = rollAndMove(state, 6)
        state = rollAndMove(state, 6)
        val third = LudoEngine.rollDice(state, 6)

        assertTrue(LudoEngine.isTurnForfeited(third))
        assertTrue("the third six can't be played", LudoEngine.movableTokens(third).isEmpty())
        val passed = LudoEngine.passTurn(third)
        assertEquals(1, passed.currentPlayerIndex)
        assertEquals(0, passed.sixesInARow)
        assertEquals("the token stays where the first two sixes put it", 12, passed.tokens.first { it.color == LudoColor.RED && it.index == 0 }.position)
    }

    @Test
    fun `a non-six in between resets the count`() {
        // A capture on a 3 earns an extra roll but breaks the run of sixes.
        val tokens = tokensWith((LudoColor.RED to 0) to 1, (LudoColor.BLUE to 0) to 17)
        var state = LudoState(tokens = tokens, currentPlayerIndex = 0, sixesInARow = 2)
        state = LudoEngine.applyMove(LudoEngine.rollDice(state, 3), LudoColor.RED, 0)
        assertEquals(0, state.currentPlayerIndex)
        assertEquals(0, state.sixesInARow)

        val six = LudoEngine.rollDice(state, 6)
        assertFalse(LudoEngine.isTurnForfeited(six))
    }

    @Test
    fun `the next player's sixes start from zero`() {
        var state = LudoState(tokens = tokensWith((LudoColor.RED to 0) to 0), currentPlayerIndex = 0)
        state = rollAndMove(state, 6)
        state = rollAndMove(state, 2)
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(0, state.sixesInARow)
    }
}
