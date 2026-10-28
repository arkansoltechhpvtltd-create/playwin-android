package com.example.playwin.feature.game.snakesandladders

import com.example.playwin.core.data.mock.MockCouponsRepository
import com.example.playwin.core.data.mock.MockGameEngineRepository
import com.example.playwin.core.data.mock.MockRewardsRepository
import com.example.playwin.feature.game.FirstTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class SnakesAndLaddersSideTest {

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

    private fun viewModel() = SnakesAndLaddersViewModel(MockGameEngineRepository(), MockRewardsRepository(), MockCouponsRepository())

    @Test
    fun `the chosen colour is the player's token, the computer's a different one`() {
        val viewModel = viewModel()
        viewModel.onColorChosen(SlTokenColor.RUBY)
        val ruby = viewModel.uiState.value
        assertEquals(SlRubyToken, ruby.tokenColors[SL_HUMAN])
        assertEquals(SlPearlToken, ruby.tokenColors[SL_COMPUTER])

        viewModel.onColorChosen(SlTokenColor.PEARL)
        val pearl = viewModel.uiState.value
        assertEquals(SlPearlToken, pearl.tokenColors[SL_HUMAN])
        assertEquals(SlGoldToken, pearl.tokenColors[SL_COMPUTER])
    }

    @Test
    fun `when the computer rolls first, its roll is the first one played`() = runBlocking {
        val viewModel = viewModel()
        viewModel.onFirstTurnChosen(FirstTurn.COMPUTER)
        val before = viewModel.uiState.value
        assertEquals(SL_COMPUTER, before.game.turn)
        // Nobody rolls until the player starts the game.
        assertFalse(before.canRoll)
        assertNull(before.lastMove)

        viewModel.onStart()
        val first = withTimeout(10_000) { viewModel.uiState.first { it.lastMove != null } }
        assertEquals(SL_COMPUTER, first.lastMove!!.player)
        // Choices are locked once the game is under way.
        viewModel.onFirstTurnChosen(FirstTurn.YOU)
        assertEquals(FirstTurn.COMPUTER, viewModel.uiState.value.firstTurn)
    }

    @Test
    fun `when you roll first, the first roll is yours`() = runBlocking {
        val viewModel = viewModel()
        viewModel.onStart()
        assertTrue(viewModel.uiState.value.canRoll)
        viewModel.onRoll()
        val first = withTimeout(10_000) { viewModel.uiState.first { it.lastMove != null } }
        assertEquals(SL_HUMAN, first.lastMove!!.player)
    }
}
