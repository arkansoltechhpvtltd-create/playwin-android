package com.example.playwin.feature.game.wordhunt

import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.wordhunt.PlacedWord
import com.example.playwin.core.domain.wordhunt.WORD_HUNT_TURN_SECONDS
import com.example.playwin.core.domain.wordhunt.WordHuntBoard
import com.example.playwin.core.domain.wordhunt.WordHuntClaim
import com.example.playwin.core.domain.wordhunt.WordHuntScoring

enum class WordHuntSide { HUMAN, COMPUTER }

data class WordHuntUiState(
    val puzzle: WordHuntBoard.Generated = WordHuntBoard.generate(),
    val humanClaims: List<WordHuntClaim> = emptyList(),
    val computerClaims: List<WordHuntClaim> = emptyList(),
    /** The human hunts first. */
    val turn: WordHuntSide = WordHuntSide.HUMAN,
    val secondsLeft: Int = WORD_HUNT_TURN_SECONDS,
    val firstSelection: Pair<Int, Int>? = null,
    val lastTurnTimedOut: Boolean = false,
    /** From the human's point of view, once the game is decided. */
    val result: GameOutcome? = null,
    val isResolving: Boolean = false,
    val wonCouponId: String? = null
) {
    val grid: List<List<Char>> get() = puzzle.grid
    val words: List<PlacedWord> get() = puzzle.words
    val humanScore: Int get() = WordHuntScoring.total(humanClaims)
    val computerScore: Int get() = WordHuntScoring.total(computerClaims)
    val canHunt: Boolean get() = result == null && !isResolving && turn == WordHuntSide.HUMAN
}
