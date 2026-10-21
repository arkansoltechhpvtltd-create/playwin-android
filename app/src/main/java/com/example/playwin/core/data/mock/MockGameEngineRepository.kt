package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.chess.ChessEngine
import com.example.playwin.core.domain.chess.GameStatus
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersRules
import com.example.playwin.core.domain.ludo.LudoEngine
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.rummy.RummyValidator
import com.example.playwin.core.domain.sudoku.SudokuRules
import com.example.playwin.core.domain.wordhunt.WordHuntScoring
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
            is GameSnapshot.ChineseCheckers -> resolveChineseCheckers(snapshot)
            is GameSnapshot.TwentyFortyEight -> resolveTwentyFortyEight(snapshot)
            is GameSnapshot.EightBall -> resolveEightBall(snapshot)
            is GameSnapshot.Carrom -> resolveCarrom(snapshot)
            is GameSnapshot.Sudoku -> resolveSudoku(snapshot)
            is GameSnapshot.WordHunt -> resolveWordHunt(snapshot)
            is GameSnapshot.Chess -> resolveChess(snapshot)
            is GameSnapshot.Ludo -> resolveLudo(snapshot)
            is GameSnapshot.Rummy -> resolveRummy(snapshot)
        }
    }

    private fun resolveChineseCheckers(snapshot: GameSnapshot.ChineseCheckers): GameOutcome {
        // The move is re-checked against the full rules, never trusted from the
        // client - an illegal move loses. The mover wins once their goal is full.
        val mover = snapshot.before.sideToMove
        val move = ChineseCheckersRules.legalMove(snapshot.before, snapshot.from, snapshot.to) ?: return GameOutcome.LOSE
        val after = ChineseCheckersRules.play(snapshot.before, move)
        return if (ChineseCheckersRules.hasWon(after, mover)) GameOutcome.WIN else GameOutcome.IN_PROGRESS
    }

    private fun resolveTwentyFortyEight(snapshot: GameSnapshot.TwentyFortyEight): GameOutcome {
        val reached2048 = snapshot.board.any { row -> row.any { it >= 2048 } }
        if (reached2048) return GameOutcome.WIN
        if (!snapshot.hasMovesLeft) return GameOutcome.LOSE
        return GameOutcome.IN_PROGRESS
    }

    private fun resolveEightBall(snapshot: GameSnapshot.EightBall): GameOutcome {
        if (!snapshot.eightBallPocketed) return GameOutcome.IN_PROGRESS
        // An 8 on the break is re-spotted, not a loss.
        if (snapshot.isBreakShot) return GameOutcome.IN_PROGRESS
        // Sinking the 8-ball only wins the round if the shooter had already
        // cleared their group and the shot was clean - otherwise it's a
        // game-losing foul, exactly like a server-authoritative engine would rule.
        return if (snapshot.shooterClearedGroup && !snapshot.scratched && !snapshot.fouled) {
            GameOutcome.WIN
        } else {
            GameOutcome.LOSE
        }
    }

    private fun resolveCarrom(snapshot: GameSnapshot.Carrom): GameOutcome {
        // Standard rule: the first player with all their coins pocketed wins the board,
        // but only once the queen is pocketed and covered.
        if (!snapshot.queenSettled) return GameOutcome.IN_PROGRESS
        return when {
            // Pocketing the opponent's last coin hands them the board, even if yours went too.
            snapshot.opponentCoinsLeft == 0 -> GameOutcome.LOSE
            snapshot.shooterCoinsLeft == 0 -> GameOutcome.WIN
            else -> GameOutcome.IN_PROGRESS
        }
    }

    private fun resolveSudoku(snapshot: GameSnapshot.Sudoku): GameOutcome =
        // Re-checked from the raw cells: every given kept, all 81 filled, no repeats.
        // A full grid that breaks a rule just isn't solved yet - the player can fix it.
        if (SudokuRules.isSolved(snapshot.givens, snapshot.grid)) GameOutcome.WIN else GameOutcome.IN_PROGRESS

    private fun resolveWordHunt(snapshot: GameSnapshot.WordHunt): GameOutcome {
        val claimedWords = (snapshot.myClaims + snapshot.opponentClaims).map { it.word }
        // A word outside the puzzle, or claimed twice, is a fabricated result.
        if (claimedWords.any { it !in snapshot.puzzleWords } || claimedWords.size != claimedWords.toSet().size) {
            return GameOutcome.LOSE
        }

        val mine = WordHuntScoring.total(snapshot.myClaims)
        val theirs = WordHuntScoring.total(snapshot.opponentClaims)
        // Out of time: the scores as they stand decide it.
        if (snapshot.timeUp) {
            return when {
                mine > theirs -> GameOutcome.WIN
                mine < theirs -> GameOutcome.LOSE
                else -> GameOutcome.DRAW
            }
        }
        val stillOpen = WordHuntScoring.maxRemaining(snapshot.puzzleWords - claimedWords.toSet())
        return when {
            mine > theirs + stillOpen -> GameOutcome.WIN
            theirs > mine + stillOpen -> GameOutcome.LOSE
            stillOpen > 0 -> GameOutcome.IN_PROGRESS
            else -> GameOutcome.DRAW
        }
    }

    private fun resolveChess(snapshot: GameSnapshot.Chess): GameOutcome {
        snapshot.resigned?.let { resigner ->
            // Resigning loses outright, whatever the material on the board.
            val lastMover = snapshot.position.sideToMove.opposite()
            return if (resigner == lastMover) GameOutcome.LOSE else GameOutcome.WIN
        }
        snapshot.timedOut?.let { flagged ->
            // Out of time loses - unless the other side couldn't mate anyway (FIDE 6.9).
            val other = flagged.opposite()
            if (!ChessEngine.hasMatingMaterial(snapshot.position, other)) return GameOutcome.DRAW
            val lastMover = snapshot.position.sideToMove.opposite()
            return if (other == lastMover) GameOutcome.WIN else GameOutcome.LOSE
        }
        // Checkmate and draws are re-derived here from the full rules engine,
        // never trusted from the client - the mover wins on checkmate.
        val status = ChessEngine.gameStatus(snapshot.position, snapshot.earlierPositions)
        return when {
            status == GameStatus.CHECKMATE -> GameOutcome.WIN
            status.isDraw -> GameOutcome.DRAW
            else -> GameOutcome.IN_PROGRESS
        }
    }

    private fun resolveLudo(snapshot: GameSnapshot.Ludo): GameOutcome =
        if (LudoEngine.hasAllTokensHome(snapshot.moverTokenPositions)) GameOutcome.WIN else GameOutcome.IN_PROGRESS

    private fun resolveRummy(snapshot: GameSnapshot.Rummy): GameOutcome =
        // An invalid declaration is a losing move in real rummy, not a free retry.
        if (RummyValidator.isValidDeclaration(snapshot.declaredHand, snapshot.wildRank)) {
            GameOutcome.WIN
        } else {
            GameOutcome.LOSE
        }
}
