package com.example.playwin.core.domain.wordhunt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WordHuntBotTest {

    private val grid = listOf(
        "XCHESSX",
        "LXXXXXB",
        "UXXXXXO",
        "DXXXXXA",
        "OXXXXXR",
        "XXXXXXD",
        "XXXXXXX"
    ).map { it.toList() }

    private val words = listOf("CHESS", "LUDO", "BOARD")

    @Test
    fun `finds horizontal and vertical words by scanning the grid`() {
        val found = WordHuntBot.findWords(grid, words).toSet()
        assertEquals(
            setOf(
                PlacedWord("CHESS", 0, 1, 0, 5),
                PlacedWord("LUDO", 1, 0, 4, 0),
                PlacedWord("BOARD", 1, 6, 5, 6)
            ),
            found
        )
    }

    @Test
    fun `goes for the longest unclaimed word`() {
        // CHESS and BOARD tie on length; alphabetical tie-break keeps it deterministic.
        assertEquals("BOARD", WordHuntBot.chooseWord(grid, words, claimed = emptySet())?.word)
        assertEquals("CHESS", WordHuntBot.chooseWord(grid, words, claimed = setOf("BOARD"))?.word)
        assertEquals("LUDO", WordHuntBot.chooseWord(grid, words, claimed = setOf("BOARD", "CHESS"))?.word)
    }

    @Test
    fun `returns null once every word is claimed`() {
        assertNull(WordHuntBot.chooseWord(grid, words, claimed = words.toSet()))
    }

    @Test
    fun `solves every generated puzzle`() {
        repeat(25) { seed ->
            val puzzle = WordHuntBoard.generate(Random(seed))
            val found = WordHuntBot.findWords(puzzle.grid, puzzle.words.map { it.word })
            assertTrue(puzzle.words.all { it in found })
        }
    }

    @Test
    fun `thinks for a human-like 4 to 11 seconds`() {
        repeat(100) { assertTrue(WordHuntBot.thinkSeconds(Random(it)) in 4..11) }
    }

    @Test
    fun `room puzzles are deterministic per room`() {
        assertEquals(WordHuntBoard.forRoom("room-a"), WordHuntBoard.forRoom("room-a"))
    }

    @Test
    fun `speed bonus is half the seconds left, capped`() {
        assertEquals(10, WordHuntScoring.bonusFor(WORD_HUNT_TURN_SECONDS))
        assertEquals(3, WordHuntScoring.bonusFor(7))
        assertEquals(0, WordHuntScoring.bonusFor(0))
        assertEquals(5 + WORD_HUNT_MAX_BONUS, WordHuntScoring.points(WordHuntClaim("CHESS", bonus = 999)))
    }
}
