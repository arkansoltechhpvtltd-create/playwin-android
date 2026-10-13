package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.wordhunt.WordHuntClaim
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WordHuntResolveTest {

    private val engine = MockGameEngineRepository()
    private val words = listOf("LUDO", "CHESS", "BINGO")

    private fun resolve(mine: List<WordHuntClaim>, theirs: List<WordHuntClaim>) = runBlocking {
        engine.resolve(GameId.WORD_HUNT, GameSnapshot.WordHunt(words, mine, theirs))
    }

    @Test
    fun `still in progress while the result could change`() {
        assertEquals(GameOutcome.IN_PROGRESS, resolve(listOf(WordHuntClaim("LUDO", 5)), emptyList()))
    }

    @Test
    fun `ends early once the leader can't be caught`() {
        // 15 + 15 = 30 vs 0 with only BINGO (max 15) left.
        val mine = listOf(WordHuntClaim("LUDO", 10), WordHuntClaim("CHESS", 10))
        assertEquals(GameOutcome.WIN, resolve(mine, emptyList()))
        assertEquals(GameOutcome.LOSE, resolve(emptyList(), mine))
    }

    private fun resolveOnTime(mine: List<WordHuntClaim>, theirs: List<WordHuntClaim>) = runBlocking {
        engine.resolve(GameId.WORD_HUNT, GameSnapshot.WordHunt(words, mine, theirs, timeUp = true))
    }

    @Test
    fun `when the game clock runs out the scores as they stand decide it`() {
        // Words are still open, which alone would keep the game going.
        assertEquals(GameOutcome.WIN, resolveOnTime(listOf(WordHuntClaim("LUDO", 5)), emptyList()))
        assertEquals(GameOutcome.LOSE, resolveOnTime(emptyList(), listOf(WordHuntClaim("LUDO", 5))))
        assertEquals(GameOutcome.DRAW, resolveOnTime(emptyList(), emptyList()))
        assertEquals(GameOutcome.DRAW, resolveOnTime(listOf(WordHuntClaim("LUDO", 1)), listOf(WordHuntClaim("CHESS", 0))))
    }

    @Test
    fun `running out of time doesn't excuse a fabricated claim`() {
        assertEquals(GameOutcome.LOSE, resolveOnTime(listOf(WordHuntClaim("POCKET", 5)), emptyList()))
    }

    @Test
    fun `compares totals once every word is claimed`() {
        val mine = listOf(WordHuntClaim("LUDO", 3), WordHuntClaim("BINGO", 3)) // 7 + 8 = 15
        assertEquals(GameOutcome.WIN, resolve(mine, listOf(WordHuntClaim("CHESS", 2)))) // 7
        assertEquals(GameOutcome.DRAW, resolve(listOf(WordHuntClaim("LUDO", 0), WordHuntClaim("BINGO", 1)), listOf(WordHuntClaim("CHESS", 5))))
    }

    @Test
    fun `fabricated claims lose`() {
        assertEquals(GameOutcome.LOSE, resolve(listOf(WordHuntClaim("NOTAWORD", 0)), emptyList()))
        assertEquals(GameOutcome.LOSE, resolve(listOf(WordHuntClaim("LUDO", 0)), listOf(WordHuntClaim("LUDO", 0))))
    }
}
