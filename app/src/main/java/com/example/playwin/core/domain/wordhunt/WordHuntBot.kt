package com.example.playwin.core.domain.wordhunt

import kotlin.random.Random

/**
 * The "Play vs Computer" Word Hunt opponent. It really searches the letter
 * grid for the listed words (left-to-right and top-to-bottom, the two
 * directions words are placed in) rather than peeking at where they were
 * hidden, then goes for the longest word still unclaimed - it's worth the
 * most base points. Its speed is human-like, not instant: see [thinkSeconds].
 */
object WordHuntBot {

    /** Every occurrence of each of [words] in [grid], reading left-to-right or top-to-bottom. */
    fun findWords(grid: List<List<Char>>, words: List<String>): List<PlacedWord> = buildList {
        val rows = grid.size
        val cols = grid.firstOrNull()?.size ?: 0
        for (word in words) {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (c + word.length <= cols && word.indices.all { grid[r][c + it] == word[it] }) {
                        add(PlacedWord(word, r, c, r, c + word.length - 1))
                    }
                    if (word.length > 1 && r + word.length <= rows && word.indices.all { grid[r + it][c] == word[it] }) {
                        add(PlacedWord(word, r, c, r + word.length - 1, c))
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
