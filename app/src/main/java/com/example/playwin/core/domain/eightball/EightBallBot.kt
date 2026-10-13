package com.example.playwin.core.domain.eightball

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The "Play vs Computer" 8-ball opponent. It picks its targets like a
 * player: any group ball while the table is open, its own group once
 * assigned, and the 8 only after its group is cleared. For each target and
 * pocket it aims the cue ball at the "ghost" spot that sends the target
 * into that pocket, keeps the makeable cuts, and then tries the best ones
 * with the real [EightBallPhysics] - so it sees balls in the way and
 * scratches coming. Results are scored with [EightBallRules]: sinking the 8
 * legally wins, sinking it early (or scratching on it) loses and is avoided
 * at all costs; own balls and keeping the turn are good, opponent balls and
 * scratches are bad. A small aiming error keeps it beatable.
 */
object EightBallBot {

    /** A shot; [cueAt] is where it places the cue ball first, when it has ball in hand. */
    data class Shot(val direction: Vec2, val power: Float, val cueAt: Vec2? = null)

    private val POWERS = listOf(0.4f, 0.65f, 0.95f)
    private const val MAX_SIMULATED_CANDIDATES = 45
    private const val MIN_CUT_COSINE = 0.35f
    private const val DECISIVE = 1000.0

    /** With ball in hand: how many of the best-looking spots it tries shots from, and how many shots from each. */
    private const val PLACEMENTS_TRIED = 4
    private const val SHOTS_PER_PLACEMENT = 9

    /** Aiming error in degrees, either way; 0 gives the bot perfect aim. */
    const val DEFAULT_AIM_ERROR_DEGREES = 1.5

    fun chooseShot(match: EightBallMatch, random: Random = Random, aimErrorDegrees: Double = DEFAULT_AIM_ERROR_DEGREES): Shot {
        // With ball in hand, shots are tried from a few good spots; otherwise from where the cue ball lies.
        val tables: List<Pair<EightBallMatch, Vec2?>> = placements(match).ifEmpty { listOf(match to null) }
        val (table, best) = tables
            .flatMap { (table, at) ->
                val shots = candidates(table).ifEmpty { listOf(safetyShot(table)) }
                (if (at == null) shots else shots.take(SHOTS_PER_PLACEMENT)).map { table to it.copy(cueAt = at) }
            }
            .maxBy { (table, shot) -> evaluate(table, shot) + random.nextDouble(0.0, 0.01) }
        if (aimErrorDegrees <= 0.0) return best

        val error = Math.toRadians(random.nextDouble(-aimErrorDegrees, aimErrorDegrees))
        val (x, y) = best.direction.x.toDouble() to best.direction.y.toDouble()
        val rotated = Vec2((x * cos(error) - y * sin(error)).toFloat(), (x * sin(error) + y * cos(error)).toFloat())
        val power = (best.power * random.nextDouble(0.93, 1.07).toFloat()).coerceIn(0.1f, 1f)
        return best.copy(direction = rotated, power = power)
    }

    /**
     * With ball in hand, the most promising spots for the cue ball (each with
     * the cue ball placed there): straight behind a target ball on its line to
     * a pocket, plus a coarse grid so the kitchen always has options - ranked
     * by the cleanest cut each leaves. Empty without ball in hand.
     */
    internal fun placements(match: EightBallMatch): List<Pair<EightBallMatch, Vec2>> {
        if (match.ballInHand == null) return emptyList()
        val r = EightBallTable.BALL_RADIUS
        val spots = mutableListOf<Vec2>()
        for (target in targets(match)) {
            for (pocket in EightBallTable.pocketCentres) {
                val toPocket = (pocket - target.position).normalizedOrZero()
                val ghost = target.position - toPocket * (r * 2f)
                for (back in listOf(3f, 6f, 10f)) spots += ghost - toPocket * (r * back)
            }
        }
        for (gx in 1..9) for (gy in 1..4) spots += Vec2(EightBallTable.WIDTH * gx / 10f, EightBallTable.HEIGHT * gy / 5f)

        return spots
            .mapNotNull { at -> EightBallRules.placeCueBall(match, at)?.let { it to at } }
            .sortedByDescending { (placed, _) -> bestCut(placed) }
            .take(PLACEMENTS_TRIED)
    }

    /** The cleanest cut (1 = dead straight) the cue ball has on any target, by geometry alone. */
    private fun bestCut(match: EightBallMatch): Float {
        val cue = match.balls.first { it.id == CUE_BALL_ID }
        return targets(match).maxOfOrNull { target ->
            EightBallTable.pocketCentres.maxOf { pocket ->
                val toPocket = (pocket - target.position).normalizedOrZero()
                val ghost = target.position - toPocket * (EightBallTable.BALL_RADIUS * 2f)
                (ghost - cue.position).normalizedOrZero().dot(toPocket)
            }
        } ?: -1f
    }

    /** How good a shot is, by simulating it with the real physics and judging it with the real rules. */
    fun evaluate(match: EightBallMatch, shot: Shot): Double {
        val result = EightBallPhysics.simulate(EightBallRules.launch(match, shot.direction, shot.power))
        val outcome = EightBallRules.resolveShot(match, result.balls, result.newlyPocketed, result.events)
        when (EightBallRules.predictedWinner(outcome)) {
            match.currentPlayer -> return DECISIVE
            match.opponent -> return -DECISIVE
        }

        val myGroup = outcome.after.playerGroups[match.currentPlayer]
        var score = 0.0
        outcome.potted.forEach { id ->
            when (ballGroupOf(id)) {
                null -> Unit
                myGroup -> score += 10.0
                else -> score -= 6.0
            }
        }
        if (outcome.fouled) score -= 15.0
        if (outcome.after.currentPlayer == match.currentPlayer) score += 3.0
        return score
    }

    /** The balls it's allowed to aim at right now. */
    internal fun targets(match: EightBallMatch): List<Ball> {
        val onTable = match.balls.filter { !it.pocketed && it.id != CUE_BALL_ID }
        val myGroup = match.playerGroups[match.currentPlayer] ?: return onTable.filter { it.id != EIGHT_BALL_ID }
        val mine = onTable.filter { ballGroupOf(it.id) == myGroup }
        return mine.ifEmpty { onTable.filter { it.id == EIGHT_BALL_ID } }
    }

    /** Ghost-ball shots at [targets], most makeable first, capped to keep the thinking time down. */
    internal fun candidates(match: EightBallMatch): List<Shot> {
        val cue = match.balls.firstOrNull { it.id == CUE_BALL_ID && !it.pocketed } ?: return emptyList()
        val aims = mutableListOf<Pair<Vec2, Float>>()
        for (target in targets(match)) {
            for (pocket in EightBallTable.pocketCentres) {
                val toPocket = (pocket - target.position).normalizedOrZero()
                val ghost = target.position - toPocket * (EightBallTable.BALL_RADIUS * 2f)
                val aim = (ghost - cue.position).normalizedOrZero()
                val cut = aim.dot(toPocket)
                if (cut >= MIN_CUT_COSINE) aims += aim to cut
            }
        }
        return aims
            .sortedByDescending { it.second }
            .take(MAX_SIMULATED_CANDIDATES / POWERS.size)
            .flatMap { (aim, _) -> POWERS.map { Shot(aim, it) } }
    }

    /** No makeable cut: roll firmly into the nearest target so the shot at least hits something of ours. */
    private fun safetyShot(match: EightBallMatch): Shot {
        val cue = match.balls.first { it.id == CUE_BALL_ID }
        val target = targets(match).minByOrNull { (it.position - cue.position).length }
            ?.position ?: Vec2(EightBallTable.RACK_APEX_X, EightBallTable.CENTER_Y)
        return Shot((target - cue.position).normalizedOrZero(), 0.6f)
    }
}
