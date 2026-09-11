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
    fun `a player wins only when all four tokens have reached the finish position`() {
        val allHome = List(4) { LudoEngine.FINISH_LOCAL_POSITION }
        val notAllHome = List(3) { LudoEngine.FINISH_LOCAL_POSITION } + listOf(10)
        assertTrue(LudoEngine.hasAllTokensHome(allHome))
        assertFalse(LudoEngine.hasAllTokensHome(notAllHome))
    }
}
