package com.example.playwin.core.domain.chess

/** A time control: each player's whole budget, sudden death (no increment). Null [seconds] means no clock at all. */
data class TimeControl(val label: String, val seconds: Int?) {
    val isUnlimited: Boolean get() = seconds == null
}

/** The time controls a player can choose from, in the order they're offered. Edit this list to change them. */
val CHESS_TIME_CONTROLS: List<TimeControl> = listOf(
    TimeControl("5 min", 5 * 60),
    TimeControl("10 min", 10 * 60),
    TimeControl("30 min", 30 * 60),
    TimeControl("1 hour", 60 * 60),
    TimeControl("3 hours", 3 * 60 * 60),
    TimeControl("No timer", null)
)

/** The time control a new game starts with. */
val DEFAULT_TIME_CONTROL: TimeControl = CHESS_TIME_CONTROLS.first { it.seconds == 10 * 60 }

/**
 * A chess clock: each side's time left, and whose clock is running. Only the
 * side to move has theirs running; a move stops it and starts the
 * opponent's. Nothing runs until the first move is made - that move starts
 * the opponent's clock. Pure state: whoever owns it feeds it elapsed time.
 */
data class ChessClock(
    val control: TimeControl,
    val whiteMillis: Long = (control.seconds ?: 0) * 1000L,
    val blackMillis: Long = (control.seconds ?: 0) * 1000L,
    /** Whose clock is counting down, or null before the first move (and always, with no timer). */
    val running: PieceColor? = null
) {
    fun millisLeft(color: PieceColor): Long = if (color == PieceColor.WHITE) whiteMillis else blackMillis

    /** The clock after [elapsedMillis] more on the running side's clock (never below zero). */
    fun tick(elapsedMillis: Long): ChessClock = when {
        control.isUnlimited || running == null || flagged != null -> this
        running == PieceColor.WHITE -> copy(whiteMillis = (whiteMillis - elapsedMillis).coerceAtLeast(0))
        else -> copy(blackMillis = (blackMillis - elapsedMillis).coerceAtLeast(0))
    }

    /** [mover] has just moved: their clock stops and the opponent's starts. */
    fun afterMove(mover: PieceColor): ChessClock =
        if (control.isUnlimited || flagged != null) this else copy(running = mover.opposite())

    /** The side whose time has run out, if either - never with no timer. */
    val flagged: PieceColor?
        get() = when {
            control.isUnlimited -> null
            whiteMillis <= 0 -> PieceColor.WHITE
            blackMillis <= 0 -> PieceColor.BLACK
            else -> null
        }

    /** Stops both clocks, for a game that's over. */
    fun stopped(): ChessClock = copy(running = null)
}
