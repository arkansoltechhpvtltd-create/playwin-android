package com.example.playwin.core.domain.carrom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CarromBotTest {

    /** Only the listed coins are on the board (everything else already pocketed); player 1 is white. */
    private fun matchWith(vararg onBoard: Pair<Int, Vec2>, colors: Map<Int, CoinColor> = mapOf(1 to CoinColor.WHITE, 2 to CoinColor.BLACK)): CarromMatch {
        val positions = onBoard.toMap()
        val coins = CarromBoard.initialSetup().map { coin ->
            when {
                coin.id == STRIKER_ID -> coin
                coin.id in positions -> coin.copy(position = positions.getValue(coin.id))
                else -> coin.copy(pocketed = true)
            }
        }
        return CarromMatch(coins = coins, currentPlayer = 1, playerColors = colors)
    }

    private fun play(match: CarromMatch, shot: CarromBot.Shot) =
        CarromPhysics.simulate(CarromRules.launch(match.coins, shot.direction, shot.power))

    @Test
    fun `pots an easy coin of its own color`() {
        // A white coin sitting on the line from the striker to the bottom-right pocket.
        val match = matchWith(1 to Vec2(600f, 755f))
        val result = play(match, CarromBot.chooseShot(match, Random(0), aimErrorDegrees = 0.0))
        assertTrue("potted ${result.newlyPocketed}", 1 in result.newlyPocketed)
        assertFalse(STRIKER_ID in result.newlyPocketed)
    }

    @Test
    fun `goes for its own coin, not the opponent's`() {
        // Mirror-image easy pots: white (ours) toward the right pocket, black toward the left.
        val match = matchWith(1 to Vec2(600f, 755f), 10 to Vec2(200f, 755f))
        val result = play(match, CarromBot.chooseShot(match, Random(0), aimErrorDegrees = 0.0))
        assertTrue(1 in result.newlyPocketed)
        assertFalse(10 in result.newlyPocketed)
    }

    @Test
    fun `scores fouls and opponent pots below a clean pot`() {
        val match = matchWith(1 to Vec2(600f, 755f), 10 to Vec2(200f, 755f))
        val atOwn = CarromBot.candidates(match).maxBy { CarromBot.evaluate(match, it) }
        val straightAtBlack = CarromBot.Shot((Vec2(200f, 755f) - CarromBoard.baselineFor(1)).normalizedOrZero(), 1f)
        assertTrue(CarromBot.evaluate(match, atOwn) > CarromBot.evaluate(match, straightAtBlack))
    }

    @Test
    fun `always produces a usable shot, even from the opening break`() {
        val shot = CarromBot.chooseShot(CarromMatch(), Random(7))
        assertEquals(1f, shot.direction.length, 0.001f)
        assertTrue(shot.power in 0.1f..1f)
    }

    @Test
    fun `with nothing makeable it still drives the striker into a coin`() {
        // A stray black coin up by player 2's baseline that no cut from the striker can pot - the
        // old fallback fired at the empty centre and missed it, turn after turn.
        val coin = Vec2(703.8f, 153.2f)
        val match = matchWith(17 to coin).let { m ->
            m.copy(currentPlayer = 2, coins = CarromRules.respotStriker(m.coins, 2))
        }
        assertTrue(CarromBot.candidates(match).isEmpty())
        val shot = CarromBot.chooseShot(match, Random(0), aimErrorDegrees = 0.0)
        val after = play(match, shot).coins.first { it.id == 17 }
        assertTrue(after.pocketed || (after.position - coin).length > 1f)
    }
}
