package com.example.playwin.feature.game.ludo

import com.example.playwin.core.data.mock.MockCouponsRepository
import com.example.playwin.core.data.mock.MockGameEngineRepository
import com.example.playwin.core.data.mock.MockRewardsRepository
import com.example.playwin.core.domain.ludo.LudoColor
import com.example.playwin.core.domain.ludo.LudoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class LudoSideTest {

    /** One thread stands in for the main thread, so the ViewModel runs as it does on a phone. */
    private lateinit var main: ExecutorCoroutineDispatcher

    @Before
    fun setUp() {
        main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        main.close()
    }

    private fun viewModel() = LudoViewModel(MockGameEngineRepository(), MockRewardsRepository(), MockCouponsRepository())

    @Test
    fun `the chosen colour is the player's, and the computer sits across the board`() = runBlocking {
        val viewModel = viewModel()
        viewModel.onColorChosen(LudoColor.GREEN)
        val state = viewModel.uiState.value

        assertEquals(LudoColor.GREEN, state.humanColor)
        assertEquals(LudoColor.BLUE, state.computerColor)
        assertEquals(listOf(LudoColor.GREEN, LudoColor.BLUE), state.game.players)
        assertEquals(setOf(LudoColor.GREEN, LudoColor.BLUE), state.game.tokens.map { it.color }.toSet())
        assertEquals(LudoEngine.TOKENS_PER_COLOR, state.game.tokens.count { it.color == LudoColor.GREEN })
    }

    @Test
    fun `nothing happens until the player starts, and then the player rolls first`() = runBlocking {
        val viewModel = viewModel()
        viewModel.onColorChosen(LudoColor.BLUE)
        assertFalse(viewModel.uiState.value.canRoll)
        // No turn clock runs while the player is choosing.
        delay(1_200)
        assertEquals(LUDO_TURN_SECONDS, viewModel.uiState.value.secondsLeft)

        viewModel.onStart()
        val started = viewModel.uiState.value
        assertTrue(started.started)
        assertEquals(LudoColor.BLUE, started.currentColor)
        assertTrue(started.isHumanTurn)
        assertTrue(started.canRoll)

        // The roll is the player's: the die lands on the player's turn, or play passes on to the computer.
        viewModel.onRollDice()
        val rolled = withTimeout(10_000) {
            viewModel.uiState.first { it.game.diceValue != null || it.currentColor == LudoColor.GREEN }
        }
        if (rolled.game.diceValue != null) assertEquals(LudoColor.BLUE, rolled.currentColor)
    }

    @Test
    fun `the colour can't be changed once the game has started`() = runBlocking {
        val viewModel = viewModel()
        viewModel.onColorChosen(LudoColor.YELLOW)
        viewModel.onStart()
        viewModel.onColorChosen(LudoColor.RED)
        assertEquals(LudoColor.YELLOW, viewModel.uiState.value.humanColor)
        assertEquals(listOf(LudoColor.YELLOW, LudoColor.RED), viewModel.uiState.value.game.players)
    }

    @Test
    fun `playing again keeps the colour and goes back to the setup`() = runBlocking {
        val viewModel = viewModel()
        viewModel.onColorChosen(LudoColor.GREEN)
        viewModel.onStart()
        viewModel.onPlayAgain()
        val state = viewModel.uiState.value
        assertEquals(LudoColor.GREEN, state.humanColor)
        assertFalse(state.started)
    }
}
