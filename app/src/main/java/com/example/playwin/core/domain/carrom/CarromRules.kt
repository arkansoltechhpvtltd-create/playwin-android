package com.example.playwin.core.domain.carrom

import kotlin.math.cos
import kotlin.math.sin

/**
 * The bonus for a covered queen. Tune it here; [CarromBot] weighs it too.
 */
const val QUEEN_POINTS = 3

/**
 * One carrom game in progress: the pieces, whose shot it is, which color
 * each player owns (decided by the first coin anyone pots), and scores.
 * Player 1 strikes from the bottom baseline, player 2 from the top.
 */
data class CarromMatch(
    val coins: List<Coin> = CarromBoard.initialSetup(),
    val currentPlayer: Int = 1,
    val playerColors: Map<Int, CoinColor> = emptyMap(),
    val scores: Map<Int, Int> = mapOf(1 to 0, 2 to 0),
    /**
     * The player who potted the queen and must still cover it on their next
     * shot, or null. While set, the queen sits pocketed but unclaimed.
     */
    val queenCoverOwedBy: Int? = null
) {
    val opponent: Int get() = 3 - currentPlayer
    val boardCleared: Boolean get() = coins.filter { it.id != STRIKER_ID }.all { it.pocketed }
}

/** What happened to the queen on one shot. */
enum class QueenEvent {
    /** Potted without a cover: the shooter must cover it on their next shot. */
    COVER_OWED,
    /** Covered: the shooter keeps the queen and scores [QUEEN_POINTS]. */
    CLAIMED,
    /** Not covered in time: the queen went back to the centre. */
    RETURNED
}

/** What one shot did: the match [after] it (striker re-spotted for whoever shoots next), and why. */
data class CarromShotOutcome(
    val shooter: Int,
    val after: CarromMatch,
    val potted: List<Int>,
    val fouled: Boolean,
    val pointsGained: Int,
    val queenEvent: QueenEvent? = null
)

/**
 * Carrom scoring and turn rules, shared by vs-Computer play, online play and
 * [CarromBot]. Potting your own color scores 1; potting the striker is a foul
 * that scores nothing and passes the turn; scoring earns another shot.
 *
 * The queen must be covered: whoever pots it has to pot one of their own
 * coins on the same shot or their very next one. A cover claims the queen
 * for [QUEEN_POINTS]; a miss (including a foul or a timed-out turn) puts it
 * back at the centre. Potting the queen earns the next shot to cover it.
 * Who has *won* once the board is clear is still decided by
 * [com.example.playwin.core.domain.repository.GameEngineRepository].
 */
object CarromRules {

    /** The board with the striker sent off along [direction] at [power] (0..1 of max speed). */
    fun launch(coins: List<Coin>, direction: Vec2, power: Float): List<Coin> {
        val speed = CarromPhysics.MAX_SHOT_SPEED * power.coerceIn(0f, 1f)
        return coins.map { if (it.id == STRIKER_ID) it.copy(velocity = direction.normalizedOrZero() * speed) else it }
    }

    fun resolveShot(match: CarromMatch, finalCoins: List<Coin>, potted: List<Int>): CarromShotOutcome {
        val shooter = match.currentPlayer
        val scratched = STRIKER_ID in potted
        val pottedCoins = potted.filter { it != STRIKER_ID }

        var colors = match.playerColors
        if (colors.isEmpty()) {
            pottedCoins.firstOrNull { coinColorOf(it) != CoinColor.QUEEN }?.let(::coinColorOf)?.let { color ->
                colors = mapOf(shooter to color, match.opponent to color.other())
            }
        }

        val ownPotted = pottedCoins.count { coinColorOf(it) != CoinColor.QUEEN && coinColorOf(it) == colors[shooter] }
        val covered = !scratched && ownPotted > 0
        val queenEvent = when {
            match.queenCoverOwedBy == shooter -> if (covered) QueenEvent.CLAIMED else QueenEvent.RETURNED
            QUEEN_ID !in pottedCoins -> null
            covered -> QueenEvent.CLAIMED
            scratched -> QueenEvent.RETURNED
            // Nothing of theirs left to cover with: the queen can't be covered, so it's theirs.
            !hasCoinsToCover(finalCoins, colors[shooter]) -> QueenEvent.CLAIMED
            else -> QueenEvent.COVER_OWED
        }

        val pointsGained = (if (scratched) 0 else ownPotted) + if (queenEvent == QueenEvent.CLAIMED) QUEEN_POINTS else 0

        val keepsTurn = !scratched && (pointsGained > 0 || queenEvent == QueenEvent.COVER_OWED)
        val nextPlayer = if (keepsTurn) shooter else match.opponent
        var coins = respotStriker(finalCoins, nextPlayer)
        if (queenEvent == QueenEvent.RETURNED) coins = respotQueen(coins)
        val after = CarromMatch(
            coins = coins,
            currentPlayer = nextPlayer,
            playerColors = colors,
            scores = match.scores + (shooter to match.scores.getValue(shooter) + pointsGained),
            queenCoverOwedBy = shooter.takeIf { queenEvent == QueenEvent.COVER_OWED }
        )
        return CarromShotOutcome(shooter, after, potted, scratched, pointsGained, queenEvent)
    }

    /** The turn passes without a shot (e.g. the shot clock ran out); an owed cover is forfeited. */
    fun passTurn(match: CarromMatch): CarromMatch {
        val coins = respotStriker(match.coins, match.opponent)
        return if (match.queenCoverOwedBy == match.currentPlayer) {
            match.copy(coins = respotQueen(coins), currentPlayer = match.opponent, queenCoverOwedBy = null)
        } else {
            match.copy(coins = coins, currentPlayer = match.opponent)
        }
    }

    fun respotStriker(coins: List<Coin>, player: Int): List<Coin> = coins.map {
        if (it.id == STRIKER_ID) it.copy(position = CarromBoard.baselineFor(player), velocity = Vec2.Zero, pocketed = false) else it
    }

    /**
     * The queen back on the board at the centre, or - if a piece is in the
     * way - the nearest free spot, searched in widening rings around it.
     */
    fun respotQueen(coins: List<Coin>): List<Coin> {
        val center = Vec2(CarromBoard.SIZE / 2f, CarromBoard.SIZE / 2f)
        val others = coins.filter { it.id != QUEEN_ID && !it.pocketed }
        fun isFree(spot: Vec2) = others.all { (it.position - spot).length >= CarromBoard.radiusOf(it.id) + CarromBoard.COIN_RADIUS }

        val spot = sequence {
            yield(center)
            // Rings half a coin apart, out to well inside the frame.
            for (ring in 1..40) {
                val radius = ring * CarromBoard.COIN_RADIUS / 2f
                for (step in 0 until 24) {
                    val angle = Math.toRadians(step * 15.0)
                    yield(Vec2(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat()))
                }
            }
        }.firstOrNull(::isFree) ?: center
        return coins.map { if (it.id == QUEEN_ID) it.copy(position = spot, velocity = Vec2.Zero, pocketed = false) else it }
    }

    /** Whether any coin the shooter could cover the queen with is still on the board. */
    private fun hasCoinsToCover(coins: List<Coin>, color: CoinColor?): Boolean = coins.any {
        it.id != STRIKER_ID && it.id != QUEEN_ID && !it.pocketed && (color == null || coinColorOf(it.id) == color)
    }
}
