package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.repository.GameEngineRepository
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock stand-in for a server-authoritative game engine. Board mechanics
 * (placing a piece, sliding tiles) live in the feature ViewModels, but the
 * *verdict* - did that move actually win the round - is only ever produced
 * here, exactly like it would be validated server-side in production.
 */
@Singleton
class MockGameEngineRepository @Inject constructor() : GameEngineRepository {

    override suspend fun resolve(gameId: GameId, snapshot: GameSnapshot): GameOutcome {
        // Simulated round-trip, as if a server validated the move.
        delay(120)

        return when (snapshot) {
            is GameSnapshot.ConnectFour -> resolveConnectFour(snapshot)
            is GameSnapshot.TwentyFortyEight -> resolveTwentyFortyEight(snapshot)
            is GameSnapshot.EightBall -> resolveEightBall(snapshot)
        }
    }

    private fun resolveConnectFour(snapshot: GameSnapshot.ConnectFour): GameOutcome {
        if (hasConnectFourWin(snapshot.board, snapshot.lastPlayer)) return GameOutcome.WIN
        if (snapshot.isBoardFull) return GameOutcome.DRAW
        return GameOutcome.IN_PROGRESS
    }

    private fun hasConnectFourWin(board: List<List<Int>>, player: Int): Boolean {
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
                        count++
                        if (count >= 4) return true
                        rr += dr
                        cc += dc
                    }
                }
            }
        }
        return false
    }

    private fun resolveTwentyFortyEight(snapshot: GameSnapshot.TwentyFortyEight): GameOutcome {
        val reached2048 = snapshot.board.any { row -> row.any { it >= 2048 } }
        if (reached2048) return GameOutcome.WIN
        if (!snapshot.hasMovesLeft) return GameOutcome.LOSE
        return GameOutcome.IN_PROGRESS
    }

    private fun resolveEightBall(snapshot: GameSnapshot.EightBall): GameOutcome {
        if (!snapshot.eightBallPocketed) return GameOutcome.IN_PROGRESS
        // Sinking the 8-ball only wins the round if the shooter had already
        // cleared their group and didn't scratch on this shot - otherwise it's
        // a game-losing foul, exactly like a server-authoritative engine would rule.
        return if (snapshot.shooterClearedGroup && !snapshot.scratched) {
            GameOutcome.WIN
        } else {
            GameOutcome.LOSE
        }
    }
}
