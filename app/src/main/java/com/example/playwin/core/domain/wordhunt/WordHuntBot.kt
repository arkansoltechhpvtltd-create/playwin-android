package com.example.playwin.core.domain.wordhunt

import kotlin.random.Random

/**
 * The "Play vs Computer" Word Hunt opponent. It really searches the letter
 * grid for the listed words, reading every way words are hidden (see
 * [WORD_HUNT_DIRECTIONS]) rather than peeking at where they were hidden,
 * then goes for the longest word still unclaimed - it's worth the most base
 * points. Its speed is human-like, not instant: see [thinkSeconds].
 */
object WordHuntBot {

    /** Every occurrence of each of [words] in [grid], read in any of the eight directions. */
    fun findWords(grid: List<List<Char>>, words: List<String>): List<PlacedWord> = buildList {
        val rows = grid.size
        val cols = grid.firstOrNull()?.size ?: 0
        fun at(r: Int, c: Int): Char? = if (r in 0 until rows && c in 0 until cols) grid[r][c] else null
        for (word in words) {
            if (word.length < 2) continue
            val span = word.length - 1
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    for ((dr, dc) in WORD_HUNT_DIRECTIONS) {
                        if (word.indices.all { at(r + dr * it, c + dc * it) == word[it] }) {
                            add(PlacedWord(word, r, c, r + dr * span, c + dc * span))
                        }
                    }
                }
            }
        }
    }

    /** The word to go for next: the longest listed word that isn't claimed yet, located on the grid. */
    fun chooseWord(grid: List<List<Char>>, words: List<String>, claimed: Set<String>): PlacedWord? =
        findWords(grid, words.filter { it !in claimed })
            .sortedWith(compareByDescending<PlacedWord> { it.word.length }.thenBy { it.word })
            .firstOrNull()

    /**
     * How long the computer "takes" to spot its word: 4-11 seconds, so a
     * quick human out-scores it on speed bonus, and a slow one doesn't.
     */
    fun thinkSeconds(random: Random = Random): Int = random.nextInt(4, 12)
}
