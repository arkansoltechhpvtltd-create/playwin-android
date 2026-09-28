package com.example.playwin.core.domain.connectfour

import kotlin.random.Random

/**
 * The "Play vs Computer" opponent. Deliberately simple and beatable: it takes
 * an immediate win, blocks the human's immediate win, avoids handing the
 * human a win on the square above its own piece, and otherwise prefers
 * central columns with a little randomness.
 *
 * Its four-in-a-row check is only for choosing a move - whether a move
 * actually ends the round is still decided by
 * [com.example.playwin.core.domain.repository.GameEngineRepository].
 */
object Connect4Bot {

    /** Returns the column [player] should drop into, or null if the board is full. */
    fun chooseColumn(board: List<List<Int>>, player: Int, opponent: Int, random: Random = Random): Int? {
        val legal = board.firstOrNull()?.indices?.filter { Connect4Logic.lowestEmptyRow(board, it) != null }
        if (legal.isNullOrEmpty()) return null

        legal.firstOrNull { col -> Connect4Logic.placePiece(board, col, player)?.let { connectsFour(it, player) } == true }
            ?.let { return it }
        legal.firstOrNull { col -> Connect4Logic.placePiece(board, col, opponent)?.let { connectsFour(it, opponent) } == true }
            ?.let { return it }

        val safe = legal.filter { col ->
            val afterMine = Connect4Logic.placePiece(board, col, player) ?: return@filter false
            val afterTheirs = Connect4Logic.placePiece(afterMine, col, opponent) ?: return@filter true
            !connectsFour(afterTheirs, opponent)
        }.ifEmpty { legal }

        val centre = (board[0].size - 1) / 2.0
        return safe.maxBy { col -> -kotlin.math.abs(col - centre) + random.nextDouble(0.0, 1.5) }
    }

    internal fun connectsFour(board: List<List<Int>>, player: Int): Boolean {
        val rows = board.size
        val cols = if (rows > 0) board[0].size else 0
        val directions = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (board[r][c] != player) continue
                for ((dr, dc) in directions) {
                    var count = 1
                    var rr = r + dr
                    var cc = c + dc
                    while (rr in 0 until rows && cc in 0 until cols && board[rr][cc] == player) {
                        if (++count >= 4) return true
                        rr += dr
                        cc += dc
                    }
                }
            }
        }
        return false
    }
}
