package com.example.playwin.core.domain.model

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
     * (solids/stripes) was fully pocketed at or before this shot.
     * @param eightBallPocketed true if the 8-ball went into a pocket on this shot.
     * @param scratched true if the cue ball was also pocketed on this shot.
     */
    data class EightBall(
        val shooterClearedGroup: Boolean,
        val eightBallPocketed: Boolean,
        val scratched: Boolean
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
}
