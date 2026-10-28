package com.example.playwin.core.domain.model

import com.example.playwin.core.domain.chess.ChessMove
import com.example.playwin.core.domain.chess.ChessPosition
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersPosition
import com.example.playwin.core.domain.chess.PieceColor
import com.example.playwin.core.domain.rummy.Card
import com.example.playwin.core.domain.rummy.Rank
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersState

/**
 * A serializable snapshot of a game's board state at the moment a player
 * believes the round is over. The UI/ViewModel never decides who won by
 * itself - it packages up what happened into one of these and asks
 * [com.example.playwin.core.domain.repository.GameEngineRepository] to
 * resolve the authoritative outcome. Today that repository is a mock that
 * re-derives the result locally; tomorrow it can be a network call to a
 * server-authoritative game engine without any UI changes.
 */
sealed interface GameSnapshot {

    /**
     * One Chinese checkers move, as played: the peg moved from hole [from] to
     * hole [to] out of [before], by the side to move there. The engine re-checks
     * the move against
     * [com.example.playwin.core.domain.chinesecheckers.ChineseCheckersRules] -
     * an illegal one loses - and rules on the result: a win once the mover's goal
     * is filled ([com.example.playwin.core.domain.chinesecheckers.ChineseCheckersRules.hasWon]),
     * a loss if the move completes the other side's.
     */
    data class ChineseCheckers(
        val before: ChineseCheckersPosition,
        val from: Int,
        val to: Int
    ) : GameSnapshot

    /**
     * The 2048 board after a move. The engine re-derives everything from it with
     * [com.example.playwin.core.domain.twenty48.Twenty48Rules] - whether the
     * target tile is reached and whether any move remains - rather than taking
     * the client's word for either.
     *
     * @param board 4 rows x 4 columns of tile values (0 = empty).
     */
    data class TwentyFortyEight(
        val board: List<List<Int>>
    ) : GameSnapshot

    /**
     * A completed 8-ball shot, from the shooter's point of view.
     *
     * @param shooterClearedGroup true if the shooter's assigned ball group
     * (solids/stripes) was fully pocketed before this shot.
     * @param eightBallPocketed true if the 8-ball went into a pocket on this shot.
     * @param scratched true if the cue ball was also pocketed on this shot.
     * @param fouled true if the shot was a foul of any kind (a scratch included).
     * @param isBreakShot true for the opening break, where a potted 8 is re-spotted.
     */
    data class EightBall(
        val shooterClearedGroup: Boolean,
        val eightBallPocketed: Boolean,
        val scratched: Boolean,
        val fouled: Boolean = scratched,
        val isBreakShot: Boolean = false
    ) : GameSnapshot

    /**
     * A completed carrom shot, from the shooter's point of view.
     *
     * @param shooterCoinsLeft the shooter's coins still on the board after this shot.
     * @param opponentCoinsLeft the opponent's coins still on the board after this shot.
     * @param queenSettled the queen is pocketed and covered (no cover still owed).
     * @param shooterScore the shooter's cumulative score, including this shot.
     * @param opponentScore the opponent's cumulative score.
     */
    data class Carrom(
        val shooterCoinsLeft: Int,
        val opponentCoinsLeft: Int,
        val queenSettled: Boolean,
        val shooterScore: Int,
        val opponentScore: Int
    ) : GameSnapshot

    /**
     * A Sudoku grid the player believes is finished.
     *
     * @param givens the puzzle as dealt, 81 cells row by row, 0 = empty.
     * @param grid the player's grid, 81 cells row by row, 0 = still empty.
     */
    data class Sudoku(
        val givens: List<Int>,
        val grid: List<Int>
    ) : GameSnapshot

    /**
     * One Snakes and Ladders roll: the game as it stood [before] it, and the
     * [roll] the engine's die ([com.example.playwin.core.domain.repository.GameEngineRepository.rollDie])
     * threw for the player to move there. The engine replays the move itself with
     * [com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersRules] -
     * the mover wins on reaching or passing square 100; a roll that isn't a die
     * face, or a game that was already over, loses.
     */
    data class SnakesAndLadders(
        val before: SnakesAndLaddersState,
        val roll: Int
    ) : GameSnapshot

    /**
     * The full board position after a move, handed to
     * [com.example.playwin.core.domain.chess.ChessEngine.gameStatus] to
     * authoritatively confirm checkmate/stalemate and the draws - never decided by the UI.
     *
     * @param earlierPositions [com.example.playwin.core.domain.chess.ChessEngine.repetitionKey]
     * of every earlier position in the game, oldest first, for threefold repetition.
     * @param timedOut the side whose clock has run out, if one has: they lose -
     * unless the other side has no mating material, which draws. The result is
     * then from the point of view of the side that moved last, as usual.
     * @param resigned the side that has resigned, if one has: they lose, whatever
     * is on the board - again from the point of view of the side that moved last.
     * @param before the position the last [move] was played from. When both are
     * given the engine re-checks that [move] was legal there and leads to
     * [position] - an illegal or doctored move loses - so a position can't be
     * made up to claim a mate.
     */
    data class Chess(
        val position: ChessPosition,
        val earlierPositions: List<String> = emptyList(),
        val timedOut: PieceColor? = null,
        val resigned: PieceColor? = null,
        val before: ChessPosition? = null,
        val move: ChessMove? = null
    ) : GameSnapshot

    /**
     * @param moverTokenPositions the 4 local positions of the mover's tokens
     * after this move; the mover wins once all 4 equal the finish position.
     */
    data class Ludo(
        val moverTokenPositions: List<Int>
    ) : GameSnapshot

    /**
     * A declared 13-card hand, handed to
     * [com.example.playwin.core.domain.rummy.RummyValidator] to authoritatively
     * confirm it forms valid groups with at least two sequences, one of them pure - never
     * decided by the UI.
     */
    data class Rummy(
        val declaredHand: List<Card>,
        val wildRank: Rank
    ) : GameSnapshot
}
