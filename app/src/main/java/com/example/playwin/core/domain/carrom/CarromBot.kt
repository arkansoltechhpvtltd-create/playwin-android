package com.example.playwin.core.domain.carrom

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The "Play vs Computer" carrom opponent. It plans the way a player does:
 * for each coin it wants (its own color and the queen, or any coin while
 * colors are still open) and each pocket, it works out the "ghost" spot the
 * striker must hit to send that coin into that pocket, keeps the cuts that
 * are actually makeable, and then *tries* the most promising ones with the
 * real [CarromPhysics] before choosing - so it accounts for other coins in
 * the way. A shot is scored by what the rules award it ([CarromRules]):
 * points and keeping the turn are good; potting the opponent's coins or the
 * striker is bad. Finally a small aiming error is added so it's beatable.
 */
object CarromBot {

    data class Shot(val direction: Vec2, val power: Float)

    private val POWERS = listOf(0.45f, 0.7f, 1f)
    private const val MAX_SIMULATED_CANDIDATES = 45
    private const val MIN_CUT_COSINE = 0.35f

    /** The score of a shot that wins (or, negated, loses) the board: beyond anything else a shot can be worth. */
    private const val DECISIVE = 1000.0

    /** Standard deviation-ish of the aiming error, in degrees; 0 gives the bot perfect aim. */
    const val DEFAULT_AIM_ERROR_DEGREES = 2.0

    fun chooseShot(match: CarromMatch, random: Random = Random, aimErrorDegrees: Double = DEFAULT_AIM_ERROR_DEGREES): Shot {
        val best = candidates(match)
            .ifEmpty { directHits(match) }
            .maxBy { evaluate(match, it) + random.nextDouble(0.0, 0.01) }
        if (aimErrorDegrees <= 0.0) return best

        val error = Math.toRadians(random.nextDouble(-aimErrorDegrees, aimErrorDegrees))
        val (x, y) = best.direction.x.toDouble() to best.direction.y.toDouble()
        val rotated = Vec2((x * cos(error) - y * sin(error)).toFloat(), (x * sin(error) + y * cos(error)).toFloat())
        val power = (best.power * random.nextDouble(0.93, 1.07).toFloat()).coerceIn(0.1f, 1f)
        return Shot(rotated, power)
    }

    /** How good a shot is, by simulating it with the real physics and scoring the result with the real rules. */
    fun evaluate(match: CarromMatch, shot: Shot): Double {
        val result = CarromPhysics.simulate(CarromRules.launch(match.coins, shot.direction, shot.power))
        val outcome = CarromRules.resolveShot(match, result.coins, result.newlyPocketed)
        when (CarromRules.winner(outcome.after, match.currentPlayer)) {
            match.currentPlayer -> return DECISIVE
            match.opponent -> return -DECISIVE
        }

        var score = outcome.pointsGained * 10.0
        if (outcome.fouled) score -= 15.0
        if (!outcome.fouled && outcome.after.currentPlayer == match.currentPlayer) score += 3.0
        // An uncovered queen is only worth something if the cover then goes in.
        if (outcome.queenEvent == QueenEvent.COVER_OWED) score += QUEEN_POINTS * 5.0
        // A coin of theirs it pots is a point for them.
        score -= outcome.opponentPointsGained * 10.0
        return score
    }

    /** Ghost-ball shots at wanted coins, most makeable first, capped to keep the thinking time down. */
    internal fun candidates(match: CarromMatch): List<Shot> {
        val striker = match.coins.firstOrNull { it.id == STRIKER_ID && !it.pocketed } ?: return emptyList()
        val myColor = match.playerColors[match.currentPlayer]
        val targets = match.coins.filter { coin ->
            coin.id != STRIKER_ID && !coin.pocketed &&
                (myColor == null || coinColorOf(coin.id) == myColor || coin.id == QUEEN_ID)
        }

        val aims = mutableListOf<Pair<Vec2, Float>>() // direction, how clean the cut is (1 = straight)
        for (coin in targets) {
            for (pocket in CarromBoard.corners) {
                val toPocket = (pocket - coin.position).normalizedOrZero()
                val ghost = coin.position - toPocket * (CarromBoard.STRIKER_RADIUS + CarromBoard.COIN_RADIUS)
                val aim = (ghost - striker.position).normalizedOrZero()
                val cut = aim.dot(toPocket)
                if (cut >= MIN_CUT_COSINE) aims += aim to cut
            }
        }
        return aims
            .sortedByDescending { it.second }
            .take(MAX_SIMULATED_CANDIDATES / POWERS.size)
            .flatMap { (aim, _) -> POWERS.map { Shot(aim, it) } }
    }

    /**
     * Nothing makeable: drive straight at a coin instead - the pack on the
     * break, a stray coin later - so the shot always moves something. Every
     * coin is tried, so a hit that pots the opponent's coin can lose out.
     */
    internal fun directHits(match: CarromMatch): List<Shot> {
        val striker = match.coins.first { it.id == STRIKER_ID }
        return match.coins
            .filter { it.id != STRIKER_ID && !it.pocketed }
            .flatMap { coin -> POWERS.map { Shot((coin.position - striker.position).normalizedOrZero(), it) } }
    }
}
