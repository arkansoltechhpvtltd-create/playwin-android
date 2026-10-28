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
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersRules
import com.example.playwin.core.domain.sudoku.SudokuRules
import com.example.playwin.core.domain.twenty48.Twenty48Rules
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Mock stand-in for a server-authoritative game engine. Board mechanics
 * (placing a piece, sliding tiles) live in the feature ViewModels, but the
 * *verdict* - did that move actually win the round - is only ever produced
 * here, exactly like it would be validated server-side in production. Dice
 * are thrown here too ([rollDie]), so no screen ever picks its own roll.
 */
@Singleton
class MockGameEngineRepository internal constructor(
    private val random: Random
) : GameEngineRepository {

    @Inject constructor() : this(Random.Default)

    override suspend fun rollDie(gameId: GameId): Int = random.nextInt(1, 7)

    override suspend fun resolve(gameId: GameId, snapshot: GameSnapshot): GameOutcome {
        // Simulated round-trip, as if a server validated the move.
        delay(120)

        return when (snapshot) {
            is GameSnapshot.ChineseCheckers -> resolveChineseCheckers(snapshot)
            is GameSnapshot.TwentyFortyEight -> resolveTwentyFortyEight(snapshot)
            is GameSnapshot.EightBall -> resolveEightBall(snapshot)
            is GameSnapshot.Carrom -> resolveCarrom(snapshot)
            is GameSnapshot.Sudoku -> resolveSudoku(snapshot)
            is GameSnapshot.SnakesAndLadders -> resolveSnakesAndLadders(snapshot)
            is GameSnapshot.Chess -> resolveChess(snapshot)
            is GameSnapshot.Ludo -> resolveLudo(snapshot)
            is GameSnapshot.Rummy -> resolveRummy(snapshot)
        }
    }

    private fun resolveChineseCheckers(snapshot: GameSnapshot.ChineseCheckers): GameOutcome {
        // The move is re-checked against the full rules, never trusted from the
        // client - an illegal move loses. The mover wins once their goal is
        // full; and since the anti-spoiling rule kicks in on a move count, the
        // move that reaches it can complete the *other* side's win - a loss.
        val mover = snapshot.before.sideToMove
        val move = ChineseCheckersRules.legalMove(snapshot.before, snapshot.from, snapshot.to) ?: return GameOutcome.LOSE
        val after = ChineseCheckersRules.play(snapshot.before, move)
        return when {
            ChineseCheckersRules.hasWon(after, mover) -> GameOutcome.WIN
            ChineseCheckersRules.hasWon(after, after.sideToMove) -> GameOutcome.LOSE
            else -> GameOutcome.IN_PROGRESS
        }
    }

    private fun resolveTwentyFortyEight(snapshot: GameSnapshot.TwentyFortyEight): GameOutcome {
        // Everything is re-derived from the board itself: a board no game could
        // show (wrong size, a tile that isn't a power of two) is fabricated - a
        // loss; then the target tile wins and a board with no move left loses.
        val board = snapshot.board
        return when {
            !Twenty48Rules.isWellFormed(board) -> GameOutcome.LOSE
            Twenty48Rules.hasReachedWinTile(board) -> GameOutcome.WIN
            !Twenty48Rules.hasMovesLeft(board) -> GameOutcome.LOSE
            else -> GameOutcome.IN_PROGRESS
        }
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

    private fun resolveSnakesAndLadders(snapshot: GameSnapshot.SnakesAndLadders): GameOutcome {
        // The move is replayed here from the position and the roll, never taken
        // from the client. A roll no die can throw, a position off the board or a
        // game that was already won is a fabricated move - a loss.
        val before = snapshot.before
        val valid = snapshot.roll in SnakesAndLaddersRules.DIE_FACES &&
            before.positions.size == SnakesAndLaddersRules.PLAYERS &&
            before.positions.all { it in SnakesAndLaddersRules.START..SnakesAndLaddersRules.FINISH } &&
            before.turn in before.positions.indices &&
            SnakesAndLaddersRules.winner(before) == null
        if (!valid) return GameOutcome.LOSE
        return if (SnakesAndLaddersRules.play(before, snapshot.roll).won) GameOutcome.WIN else GameOutcome.IN_PROGRESS
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
        // The move itself is re-checked when it's sent: it must be legal from the
        // position before it, lead to the position reported, and that earlier
        // position must be the last one in the game's history. Anything else was
        // doctored - a loss for the mover.
        if (snapshot.move != null || snapshot.before != null) {
            val before = snapshot.before ?: return GameOutcome.LOSE
            val sent = snapshot.move ?: return GameOutcome.LOSE
            val legal = ChessEngine.legalMove(before, sent.from, sent.to, sent.promotion) ?: return GameOutcome.LOSE
            if (ChessEngine.applyMove(before, legal) != snapshot.position) return GameOutcome.LOSE
            val lastKey = snapshot.earlierPositions.lastOrNull()
            if (lastKey != null && lastKey != ChessEngine.repetitionKey(before)) return GameOutcome.LOSE
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
