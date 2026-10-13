package com.example.playwin.core.domain.model

import com.example.playwin.core.domain.chess.ChessPosition
import com.example.playwin.core.domain.rummy.Card
import com.example.playwin.core.domain.rummy.Rank
import com.example.playwin.core.domain.wordhunt.WordHuntClaim

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
     * @param board 6 rows x 7 columns, 0 = empty, 1 = player one, 2 = player two.
     * @param lastPlayer the player who just moved (1 or 2).
     * @param isBoardFull true when no columns remain playable.
     */
    data class ConnectFour(
        val board: List<List<Int>>,
        val lastPlayer: Int,
        val isBoardFull: Boolean
    ) : GameSnapshot

    /**
     * @param board 4 rows x 4 columns of tile values (0 = empty).
     * @param hasMovesLeft whether at least one legal move remains.
     */
    data class TwentyFortyEight(
        val board: List<List<Int>>,
        val hasMovesLeft: Boolean
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
     * @param boardCleared true once every coin and the queen have been pocketed.
     * @param shooterScore the shooter's cumulative score, including this shot.
     * @param opponentScore the opponent's cumulative score.
     */
    data class Carrom(
        val boardCleared: Boolean,
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
     * A head-to-head Word Hunt position, from the point of view of the player
     * asking. The engine re-derives the score itself: every claimed word must
     * be one of [puzzleWords] and claimed at most once across both players,
     * and bonuses are capped - see
     * [com.example.playwin.core.domain.wordhunt.WordHuntScoring].
     *
     * @param puzzleWords every word hidden in this round's grid.
     * @param myClaims the words this player found, with their speed bonuses.
     * @param opponentClaims the words the other player found.
     */
    data class WordHunt(
        val puzzleWords: List<String>,
        val myClaims: List<WordHuntClaim>,
        val opponentClaims: List<WordHuntClaim>
    ) : GameSnapshot

    /**
     * The full board position after a move, handed to
     * [com.example.playwin.core.domain.chess.ChessEngine.gameStatus] to
     * authoritatively confirm checkmate/stalemate and the draws - never decided by the UI.
     *
     * @param earlierPositions [com.example.playwin.core.domain.chess.ChessEngine.repetitionKey]
     * of every earlier position in the game, oldest first, for threefold repetition.
     */
    data class Chess(
        val position: ChessPosition,
        val earlierPositions: List<String> = emptyList()
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
