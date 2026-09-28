package com.example.playwin.core.domain.carrom

/**
 * One carrom game in progress: the pieces, whose shot it is, which color
 * each player owns (decided by the first coin anyone pots), and scores.
 * Player 1 strikes from the bottom baseline, player 2 from the top.
 */
data class CarromMatch(
    val coins: List<Coin> = CarromBoard.initialSetup(),
    val currentPlayer: Int = 1,
    val playerColors: Map<Int, CoinColor> = emptyMap(),
    val scores: Map<Int, Int> = mapOf(1 to 0, 2 to 0)
) {
    val opponent: Int get() = 3 - currentPlayer
    val boardCleared: Boolean get() = coins.filter { it.id != STRIKER_ID }.all { it.pocketed }
}

/** What one shot did: the match [after] it (striker re-spotted for whoever shoots next), and why. */
data class CarromShotOutcome(
    val shooter: Int,
    val after: CarromMatch,
    val potted: List<Int>,
    val fouled: Boolean,
    val pointsGained: Int
)

/**
 * Carrom scoring and turn rules, shared by vs-Computer play, online play and
 * [CarromBot]. Potting your own color scores 1, the queen 3; potting the
 * striker is a foul that scores nothing and passes the turn; scoring earns
 * another shot. Who has *won* once the board is clear is still decided by
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

        val pointsGained = if (scratched) {
            0
        } else {
            pottedCoins.sumOf { id ->
                when (coinColorOf(id)) {
                    CoinColor.QUEEN -> 3
                    colors[shooter] -> 1
                    else -> 0
                }
            }
        }

        val nextPlayer = if (!scratched && pointsGained > 0) shooter else match.opponent
        val after = CarromMatch(
            coins = respotStriker(finalCoins, nextPlayer),
            currentPlayer = nextPlayer,
            playerColors = colors,
            scores = match.scores + (shooter to match.scores.getValue(shooter) + pointsGained)
        )
        return CarromShotOutcome(shooter, after, potted, scratched, pointsGained)
    }

    /** The turn passes without a shot (e.g. the shot clock ran out). */
    fun passTurn(match: CarromMatch): CarromMatch =
        match.copy(coins = respotStriker(match.coins, match.opponent), currentPlayer = match.opponent)

    fun respotStriker(coins: List<Coin>, player: Int): List<Coin> = coins.map {
        if (it.id == STRIKER_ID) it.copy(position = CarromBoard.baselineFor(player), velocity = Vec2.Zero, pocketed = false) else it
    }
}
