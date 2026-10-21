package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersBoard
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersPosition
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersRules
import com.example.playwin.core.domain.chinesecheckers.Hole
import com.example.playwin.core.domain.ludo.LudoEngine
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The engine's verdicts for the games whose rules live entirely in the resolver. */
class GameVerdictResolveTest {

    private val engine = MockGameEngineRepository()

    private fun resolve(gameId: GameId, snapshot: GameSnapshot) = runBlocking { engine.resolve(gameId, snapshot) }

    // --- Chinese checkers ---

    private fun chineseCheckers(before: ChineseCheckersPosition, from: Int, to: Int) =
        resolve(GameId.CHINESE_CHECKERS, GameSnapshot.ChineseCheckers(before, from, to))

    @Test
    fun `chinese checkers - the move that fills the goal wins, any other legal move plays on`() {
        // Player 1 has nine pegs in the top point and the tenth one step below the last hole.
        val goal = ChineseCheckersBoard.goal(1)
        val open = goal.first { hole -> ChineseCheckersBoard.neighbours[hole].any { it >= 0 && it !in goal } }
        val outside = ChineseCheckersBoard.neighbours[open].first { it >= 0 && it !in goal }
        val cells = MutableList(ChineseCheckersBoard.holes.size) { 0 }
        goal.filter { it != open }.forEach { cells[it] = 1 }
        cells[outside] = 1
        val position = ChineseCheckersPosition(cells, sideToMove = 1)

        assertEquals(GameOutcome.WIN, chineseCheckers(position, outside, open))
        val elsewhere = ChineseCheckersRules.movesFrom(position, outside).first { it.to != open }
        assertEquals(GameOutcome.IN_PROGRESS, chineseCheckers(position, outside, elsewhere.to))
    }

    @Test
    fun `chinese checkers - the move that reaches the anti-spoiling count loses if the other side's goal is already full`() {
        // Player 1's goal: nine of player 1's pegs and one of player 2's that never left home.
        val goal = ChineseCheckersBoard.goal(1).toList()
        val cells = MutableList(ChineseCheckersBoard.holes.size) { 0 }
        goal.forEachIndexed { i, hole -> cells[hole] = if (i < 9) 1 else 2 }
        // Player 2 has a free peg in the middle to move with.
        val middle = requireNotNull(ChineseCheckersBoard.indexOf(Hole(0, 0)))
        cells[middle] = 2
        val step = ChineseCheckersBoard.neighbours[middle].first { it >= 0 }
        val lastTurnBefore = 2 * ChineseCheckersRules.ANTI_SPOILING_MOVES - 1

        // Player 2's move is the 60th turn: player 1's goal now counts as filled.
        val position = ChineseCheckersPosition(cells, sideToMove = 2, turnsPlayed = lastTurnBefore)
        assertEquals(GameOutcome.LOSE, chineseCheckers(position, middle, step))
        // A turn earlier, the same move plays on.
        assertEquals(GameOutcome.IN_PROGRESS, chineseCheckers(position.copy(turnsPlayed = lastTurnBefore - 1), middle, step))
    }

    @Test
    fun `chinese checkers - an illegal move loses, never plays`() {
        val start = ChineseCheckersRules.initialPosition()
        val peg = start.pegsOf(1).first()
        // Straight across the board in one go: no such move.
        val farAway = ChineseCheckersBoard.goal(1).first()
        assertEquals(GameOutcome.LOSE, chineseCheckers(start, peg, farAway))
        // Moving the other side's peg.
        val empty = { hole: Int -> ChineseCheckersBoard.neighbours[hole].firstOrNull { it >= 0 && start.cells[it] == 0 } }
        val theirs = start.pegsOf(2).first { empty(it) != null }
        assertEquals(GameOutcome.LOSE, chineseCheckers(start, theirs, empty(theirs)!!))
    }

    // --- 2048 ---

    @Test
    fun `2048 wins on the 2048 tile, loses only when stuck`() {
        val withTile = List(4) { r -> List(4) { c -> if (r == 0 && c == 0) 2048 else 0 } }
        assertEquals(GameOutcome.WIN, resolve(GameId.TWENTY_FORTY_EIGHT, GameSnapshot.TwentyFortyEight(withTile, hasMovesLeft = false)))
        val plain = List(4) { List(4) { 2 } }
        assertEquals(GameOutcome.IN_PROGRESS, resolve(GameId.TWENTY_FORTY_EIGHT, GameSnapshot.TwentyFortyEight(plain, hasMovesLeft = true)))
        assertEquals(GameOutcome.LOSE, resolve(GameId.TWENTY_FORTY_EIGHT, GameSnapshot.TwentyFortyEight(plain, hasMovesLeft = false)))
    }

    // --- Ludo ---

    @Test
    fun `ludo wins only with all four tokens home`() {
        val home = LudoEngine.FINISH_LOCAL_POSITION
        assertEquals(GameOutcome.WIN, resolve(GameId.LUDO, GameSnapshot.Ludo(List(4) { home })))
        assertEquals(GameOutcome.IN_PROGRESS, resolve(GameId.LUDO, GameSnapshot.Ludo(listOf(home, home, home, home - 1))))
    }

    // --- 8-ball ---

    @Test
    fun `8-ball potting the 8 wins only with your group cleared and no scratch`() {
        fun eight(cleared: Boolean, scratched: Boolean) =
            resolve(GameId.EIGHT_BALL, GameSnapshot.EightBall(cleared, eightBallPocketed = true, scratched = scratched))
        assertEquals(GameOutcome.WIN, eight(cleared = true, scratched = false))
        assertEquals(GameOutcome.LOSE, eight(cleared = false, scratched = false))
        assertEquals(GameOutcome.LOSE, eight(cleared = true, scratched = true))
        assertEquals(
            GameOutcome.IN_PROGRESS,
            resolve(GameId.EIGHT_BALL, GameSnapshot.EightBall(shooterClearedGroup = true, eightBallPocketed = false, scratched = true))
        )
    }
}
