package com.example.playwin.feature.game.twenty48

/**
 * Pure board mechanics for 2048: sliding, merging, spawning and detecting
 * whether any legal move remains. None of this decides whether the *round*
 * is won - that verdict always comes from
 * [com.example.playwin.core.domain.repository.GameEngineRepository].
 */
internal object Twenty48Board {

    fun emptyBoardWithStartingTiles(): List<List<Int>> {
        var board = List(GRID_SIZE) { List(GRID_SIZE) { 0 } }
        board = spawnRandomTile(board)
        board = spawnRandomTile(board)
        return board
    }

    fun spawnRandomTile(board: List<List<Int>>): List<List<Int>> {
        val emptyCells = buildList {
            for (r in board.indices) {
                for (c in board[r].indices) {
                    if (board[r][c] == 0) add(r to c)
                }
            }
        }
        if (emptyCells.isEmpty()) return board
        val (r, c) = emptyCells.random()
        val value = if ((0..9).random() == 0) 4 else 2
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
