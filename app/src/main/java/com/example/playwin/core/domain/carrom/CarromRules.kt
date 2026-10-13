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
    val queenCoverOwedBy: Int? = null,
    /**
     * Dues each player still owes: fouls committed before they had a coin
     * of their own pocketed to give back. Paid off with their next own coins.
     */
    val duesOwed: Map<Int, Int> = emptyMap()
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

/** How a due (the penalty for a foul) was settled on one shot. */
enum class DueEvent {
    /** Fouled, and one of the shooter's pocketed coins went back to the centre. */
    PAID_ON_FOUL,
    /** Fouled with no coin of their own pocketed yet: the due waits for their next one. */
    OWED,
    /** A clean shot whose own coins went back to the centre to pay off dues owed. */
    PAID_FROM_POT
}

/**
 * What one shot did: the match [after] it (striker re-spotted for whoever
 * shoots next), and why. [pointsGained] goes negative when a foul's due
 * takes back a scored coin.
 */
data class CarromShotOutcome(
    val shooter: Int,
    val after: CarromMatch,
    val potted: List<Int>,
    val fouled: Boolean,
    val pointsGained: Int,
    val queenEvent: QueenEvent? = null,
    val dueEvent: DueEvent? = null,
    /** Coins put back at the centre by this shot (the queen included, if it went back). */
    val returnedToCentre: List<Int> = emptyList()
)

/**
 * Carrom scoring and turn rules, shared by vs-Computer play, online play and
 * [CarromBot]. Potting your own color scores 1, and scoring earns another shot.
 *
 * Fouls follow the standard rule: potting the striker passes the turn, every
 * coin potted on that shot goes back to the centre, and the fouler pays a
 * due - one of their own pocketed coins returns to the centre too, losing
 * its point. With none pocketed yet, the due is owed and their next own coin
 * pays it instead of scoring.
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
        if (STRIKER_ID in potted) return resolveFoul(match, finalCoins, potted)

        val shooter = match.currentPlayer
        val pottedCoins = potted.filter { it != STRIKER_ID }

        var colors = match.playerColors
        if (colors.isEmpty()) {
            pottedCoins.firstOrNull { coinColorOf(it) != CoinColor.QUEEN }?.let(::coinColorOf)?.let { color ->
                colors = mapOf(shooter to color, match.opponent to color.other())
            }
        }

        val ownPotted = pottedCoins.filter { coinColorOf(it) != CoinColor.QUEEN && coinColorOf(it) == colors[shooter] }
        val covered = ownPotted.isNotEmpty()
        val queenEvent = when {
            match.queenCoverOwedBy == shooter -> if (covered) QueenEvent.CLAIMED else QueenEvent.RETURNED
            QUEEN_ID !in pottedCoins -> null
            covered -> QueenEvent.CLAIMED
            // Nothing of theirs left to cover with: the queen can't be covered, so it's theirs.
            !hasCoinsToCover(finalCoins, colors[shooter]) -> QueenEvent.CLAIMED
            else -> QueenEvent.COVER_OWED
        }

        // Own coins pay off any dues owed first: each goes back to the centre instead of scoring.
        val duesOwed = match.duesOwed[shooter] ?: 0
        val payingDues = ownPotted.take(duesOwed)
        val pointsGained = ownPotted.size - payingDues.size + if (queenEvent == QueenEvent.CLAIMED) QUEEN_POINTS else 0

        val keepsTurn = covered || queenEvent == QueenEvent.CLAIMED || queenEvent == QueenEvent.COVER_OWED
        val nextPlayer = if (keepsTurn) shooter else match.opponent
        val returned = payingDues + listOfNotNull(QUEEN_ID.takeIf { queenEvent == QueenEvent.RETURNED })
        val after = CarromMatch(
            coins = returnToCentre(respotStriker(finalCoins, nextPlayer), returned),
            currentPlayer = nextPlayer,
            playerColors = colors,
            scores = match.scores + (shooter to match.scores.getValue(shooter) + pointsGained),
            queenCoverOwedBy = shooter.takeIf { queenEvent == QueenEvent.COVER_OWED },
            duesOwed = withDues(match.duesOwed, shooter, duesOwed - payingDues.size)
        )
        return CarromShotOutcome(
            shooter, after, potted, fouled = false, pointsGained, queenEvent,
            dueEvent = DueEvent.PAID_FROM_POT.takeIf { payingDues.isNotEmpty() },
            returnedToCentre = returned
        )
    }

    /**
     * Potting the striker: the turn passes, every coin potted on the shot
     * goes back to the centre (so no colors are decided and an owed or freshly
     * potted queen returns), and the fouler pays a due - one of their own
     * coins pocketed on an earlier shot comes back out, losing its point, or
     * the due is owed if they have none.
     */
    private fun resolveFoul(match: CarromMatch, finalCoins: List<Coin>, potted: List<Int>): CarromShotOutcome {
        val shooter = match.currentPlayer
        val pottedCoins = potted.filter { it != STRIKER_ID }
        val queenEvent = QueenEvent.RETURNED.takeIf { match.queenCoverOwedBy == shooter || QUEEN_ID in pottedCoins }

        val myColor = match.playerColors[shooter]
        val dueCoin = match.coins.firstOrNull {
            it.pocketed && it.id != STRIKER_ID && it.id != QUEEN_ID && myColor != null && coinColorOf(it.id) == myColor
        }?.id
        val duesOwed = (match.duesOwed[shooter] ?: 0) + if (dueCoin == null) 1 else 0
        val pointsGained = if (dueCoin != null) -1 else 0

        val returned = (pottedCoins + listOfNotNull(dueCoin, QUEEN_ID.takeIf { queenEvent != null })).distinct()
        val after = CarromMatch(
            coins = returnToCentre(respotStriker(finalCoins, match.opponent), returned),
            currentPlayer = match.opponent,
            playerColors = match.playerColors,
            scores = match.scores + (shooter to match.scores.getValue(shooter) + pointsGained),
            queenCoverOwedBy = null,
            duesOwed = withDues(match.duesOwed, shooter, duesOwed)
        )
        return CarromShotOutcome(
            shooter, after, potted, fouled = true, pointsGained, queenEvent,
            dueEvent = if (dueCoin != null) DueEvent.PAID_ON_FOUL else DueEvent.OWED,
            returnedToCentre = returned
        )
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

    /** The queen back on the board at the centre, or the nearest free spot (see [respotAtCentre]). */
    fun respotQueen(coins: List<Coin>): List<Coin> = respotAtCentre(coins, QUEEN_ID)

    /** Each of [ids] back on the board around the centre, placed one after another so none overlap. */
    fun returnToCentre(coins: List<Coin>, ids: List<Int>): List<Coin> = ids.fold(coins, ::respotAtCentre)

    /**
     * Coin [id] back on the board at the centre, or - if a piece is in the
     * way - the nearest free spot, searched in widening rings around it.
     */
    fun respotAtCentre(coins: List<Coin>, id: Int): List<Coin> {
        val center = Vec2(CarromBoard.SIZE / 2f, CarromBoard.SIZE / 2f)
        val others = coins.filter { it.id != id && !it.pocketed }
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
        return coins.map { if (it.id == id) it.copy(position = spot, velocity = Vec2.Zero, pocketed = false) else it }
    }

    /** [dues] with [player] owing [count]; a player who owes nothing has no entry. */
    private fun withDues(dues: Map<Int, Int>, player: Int, count: Int): Map<Int, Int> =
        if (count > 0) dues + (player to count) else dues - player

    /** Whether any coin the shooter could cover the queen with is still on the board. */
    private fun hasCoinsToCover(coins: List<Coin>, color: CoinColor?): Boolean = coins.any {
        it.id != STRIKER_ID && it.id != QUEEN_ID && !it.pocketed && (color == null || coinColorOf(it.id) == color)
    }
}
