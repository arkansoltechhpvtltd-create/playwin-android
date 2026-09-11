package com.example.playwin.feature.game.wordhunt

data class WordHuntUiState(
    val puzzle: WordHuntBoard.Generated = WordHuntBoard.generate(),
    val foundWords: Set<String> = emptySet(),
    val firstSelection: Pair<Int, Int>? = null,
    val secondsLeft: Int = WORD_HUNT_TIME_SECONDS,
    val hasWon: Boolean = false,
    val hasLost: Boolean = false,
    val isResolving: Boolean = false,
    val wonCouponId: String? = null
) {
    val grid: List<List<Char>> get() = puzzle.grid
    val words: List<PlacedWord> get() = puzzle.words

    val foundCells: Set<Pair<Int, Int>>
        get() = words.filter { it.word in foundWords }.flatMap { it.cells() }.toSet()
}
