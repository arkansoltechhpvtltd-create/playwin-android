package com.example.playwin.core.domain.twenty48

import kotlin.random.Random

/** The four ways the tiles can be swiped. */
enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }

/**
 * Pure 2048 rules on a [GRID_SIZE] x [GRID_SIZE] board (0 = empty): sliding,
 * merging (each tile merges at most once per move, so 2-2-2-2 becomes 4-4,
 * never 8), spawning a 2 (or, one time in ten, a 4) after a move that changed
 * the board, and whether any move remains. The game engine re-derives the
 * verdict from these - reaching [WIN_TILE] wins, no move left loses - so it
 * never trusts a client's word on either.
 */
object Twenty48Rules {
    const val GRID_SIZE = 4
    const val WIN_TILE = 2048

    fun emptyBoardWithStartingTiles(random: Random = Random): List<List<Int>> {
        var board = List(GRID_SIZE) { List(GRID_SIZE) { 0 } }
        board = spawnRandomTile(board, random)
        board = spawnRandomTile(board, random)
        return board
    }

    fun spawnRandomTile(board: List<List<Int>>, random: Random = Random): List<List<Int>> {
        val emptyCells = buildList {
            for (r in board.indices) {
                for (c in board[r].indices) {
                    if (board[r][c] == 0) add(r to c)
                }
            }
        }
        if (emptyCells.isEmpty()) return board
        val (r, c) = emptyCells.random(random)
        val value = if (random.nextInt(10) == 0) 4 else 2
        val newBoard = board.map { it.toMutableList() }
        newBoard[r][c] = value
        return newBoard
    }

    /** Returns the new board and whether any tile actually moved or merged. */
    fun move(board: List<List<Int>>, direction: SwipeDirection): Pair<List<List<Int>>, Boolean> {
        val working = when (direction) {
            SwipeDirection.LEFT -> board
            SwipeDirection.RIGHT -> reverseRows(board)
            SwipeDirection.UP -> transpose(board)
            SwipeDirection.DOWN -> reverseRows(transpose(board))
        }

        val moved = working.map { moveRowLeft(it) }

        val result = when (direction) {
            SwipeDirection.LEFT -> moved
            SwipeDirection.RIGHT -> reverseRows(moved)
            SwipeDirection.UP -> transpose(moved)
            SwipeDirection.DOWN -> transpose(reverseRows(moved))
        }

        return result to (result != board)
    }

    fun hasMovesLeft(board: List<List<Int>>): Boolean {
        for (r in board.indices) {
            for (c in board[r].indices) {
                if (board[r][c] == 0) return true
                if (c + 1 < board[r].size && board[r][c] == board[r][c + 1]) return true
                if (r + 1 < board.size && board[r][c] == board[r + 1][c]) return true
            }
        }
        return false
    }

    fun hasReachedWinTile(board: List<List<Int>>): Boolean = board.any { row -> row.any { it >= WIN_TILE } }

    /**
     * Whether [board] is a board a real game could show: [GRID_SIZE] rows of
     * [GRID_SIZE] cells, each empty or a power of two from 2 up. Anything else
     * was made up, not played.
     */
    fun isWellFormed(board: List<List<Int>>): Boolean =
        board.size == GRID_SIZE && board.all { row ->
            row.size == GRID_SIZE && row.all { it == 0 || (it >= 2 && it and (it - 1) == 0) }
        }

    private fun moveRowLeft(row: List<Int>): List<Int> {
        val nonZero = row.filter { it != 0 }
        val merged = mutableListOf<Int>()
        var i = 0
        while (i < nonZero.size) {
            if (i + 1 < nonZero.size && nonZero[i] == nonZero[i + 1]) {
                merged.add(nonZero[i] * 2)
                i += 2
            } else {
                merged.add(nonZero[i])
                i += 1
            }
        }
        while (merged.size < row.size) merged.add(0)
        return merged
    }

    private fun transpose(board: List<List<Int>>): List<List<Int>> {
        val n = board.size
        return List(n) { r -> List(n) { c -> board[c][r] } }
    }

    private fun reverseRows(board: List<List<Int>>): List<List<Int>> = board.map { it.reversed() }
}
