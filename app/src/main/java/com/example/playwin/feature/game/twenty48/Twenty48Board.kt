package com.example.playwin.feature.game.twenty48

import com.example.playwin.core.domain.twenty48.Twenty48Rules

/**
 * The 2048 board mechanics as the screen uses them: sliding, merging,
 * spawning and detecting whether any legal move remains. The rules themselves
 * live in [Twenty48Rules], where the game engine re-derives the verdict from
 * the same code - none of this decides whether the *round* is won.
 */
internal object Twenty48Board {

    fun emptyBoardWithStartingTiles(): List<List<Int>> = Twenty48Rules.emptyBoardWithStartingTiles()

    fun spawnRandomTile(board: List<List<Int>>): List<List<Int>> = Twenty48Rules.spawnRandomTile(board)

    /** Returns the new board and whether any tile actually moved or merged. */
    fun move(board: List<List<Int>>, direction: SwipeDirection): Pair<List<List<Int>>, Boolean> =
        Twenty48Rules.move(board, direction)

    fun hasMovesLeft(board: List<List<Int>>): Boolean = Twenty48Rules.hasMovesLeft(board)
}
