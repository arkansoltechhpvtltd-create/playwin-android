package com.example.playwin.core.domain

import com.example.playwin.core.domain.carrom.CarromBot
import com.example.playwin.core.domain.carrom.CarromMatch
import com.example.playwin.core.domain.carrom.CarromPhysics
import com.example.playwin.core.domain.carrom.CarromRules
import com.example.playwin.core.domain.eightball.EightBallBot
import com.example.playwin.core.domain.eightball.EightBallMatch
import com.example.playwin.core.domain.eightball.EightBallPhysics
import com.example.playwin.core.domain.eightball.EightBallRules
import com.example.playwin.core.domain.ludo.LudoBot
import com.example.playwin.core.domain.ludo.LudoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The computer playing both sides always brings a game to its end - no position it can't get out of. */
class GamesFinishTest {

    @Test
    fun `ludo always finishes`() {
        repeat(20) { seed ->
            var state = LudoEngine.initialState()
            var turns = 0
            while (state.players.none { color -> LudoEngine.hasAllTokensHome(state.tokens.filter { it.color == color }.map { it.position }) }) {
                val rolled = LudoEngine.rollDice(state)
                state = LudoBot.chooseToken(rolled)?.let { LudoEngine.applyMove(rolled, it.color, it.index) } ?: LudoEngine.passTurn(rolled)
                turns++
                assertTrue("game $seed still going after $turns turns", turns < 20_000)
            }
        }
    }

    @Test
    fun `carrom always ends with someone clearing their coins`() = repeat(10) { seed ->
        var match = CarromMatch()
        var shots = 0
        val random = Random(seed)
        while (true) {
            val shooter = match.currentPlayer
            val shot = CarromBot.chooseShot(match, random)
            val result = CarromPhysics.simulate(CarromRules.launch(match.coins, shot.direction, shot.power))
            match = CarromRules.resolveShot(match, result.coins, result.newlyPocketed).after
            shots++
            val winner = CarromRules.winner(match, shooter)
            if (winner != null) {
                assertEquals("the winner has cleared their coins", 0, match.coinsLeft(winner))
                assertTrue("the queen was covered first", match.queenSettled)
                break
            }
            assertTrue("no winner after $shots shots", shots < 600)
        }
    }

    @Test
    fun `eight-ball always sinks the eight`() = repeat(10) { seed ->
        var match = EightBallMatch()
        var shots = 0
        val random = Random(seed)
        while (true) {
            val shot = EightBallBot.chooseShot(match, random)
            val result = EightBallPhysics.simulate(EightBallRules.launch(match, shot.direction, shot.power))
            val outcome = EightBallRules.resolveShot(match, result.balls, result.newlyPocketed, result.events)
            if (outcome.eightBallPocketed && !outcome.wasBreak) break
            match = outcome.after
            shots++
            assertTrue("8-ball still on the table after $shots shots", shots < 400)
        }
    }
}
