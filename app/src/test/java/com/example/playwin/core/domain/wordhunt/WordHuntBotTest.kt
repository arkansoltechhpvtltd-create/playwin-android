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
    fun `finds backwards and diagonal words too`() {
        val twisted = listOf(
            "SSEHCXX",
            "LXXXXXX",
            "XUXXXXD",
            "XXDXXRX",
            "XXXOAXX",
            "XXXOXXX",
            "XXBXXXX"
        ).map { it.toList() }
        val found = WordHuntBot.findWords(twisted, words).toSet()
        assertEquals(
            setOf(
                PlacedWord("CHESS", 0, 4, 0, 0), // right to left
                PlacedWord("LUDO", 1, 0, 4, 3), // down-right
                PlacedWord("BOARD", 6, 2, 2, 6) // up-right
            ),
            found
        )
    }

    @Test
    fun `a word's cells follow its direction, whichever way it runs`() {
        assertEquals(listOf(0 to 4, 0 to 3, 0 to 2, 0 to 1, 0 to 0), PlacedWord("CHESS", 0, 4, 0, 0).cells())
        assertEquals(listOf(6 to 2, 5 to 3, 4 to 4, 3 to 5, 2 to 6), PlacedWord("BOARD", 6, 2, 2, 6).cells())
    }

    @Test
    fun `a diagonal or backwards word is selected by tapping either end`() {
        val diagonal = PlacedWord("BOARD", 6, 2, 2, 6)
        assertTrue(diagonal.matches(6 to 2, 2 to 6))
        assertTrue(diagonal.matches(2 to 6, 6 to 2))
        assertTrue(!diagonal.matches(6 to 2, 3 to 5))
    }

    @Test
    fun `generated puzzles hide words in all eight directions, each readable along its cells`() {
        val seen = mutableSetOf<Pair<Int, Int>>()
        repeat(60) { seed ->
            val puzzle = WordHuntBoard.generate(Random(seed))
            for (placed in puzzle.words) {
                val read = placed.cells().map { (r, c) -> puzzle.grid[r][c] }.joinToString("")
                assertEquals(placed.word, read)
                seen += (placed.endRow - placed.startRow).coerceIn(-1, 1) to (placed.endCol - placed.startCol).coerceIn(-1, 1)
            }
        }
        assertEquals(WORD_HUNT_DIRECTIONS.toSet(), seen)
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
