package com.example.playwin.feature.game.eightball

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class EightBallSideTest {

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

    private fun viewModel() = EightBallViewModel(MockGameEngineRepository(), MockRewardsRepository(), MockCouponsRepository())

    @Test
    fun `when the computer breaks, it takes the break shot`() = runBlocking {
        val viewModel = viewModel()
        viewModel.onFirstTurnChosen(FirstTurn.COMPUTER)
        val before = viewModel.uiState.value
        assertEquals(EIGHT_BALL_COMPUTER, before.currentPlayer)
        assertTrue(before.match.isBreak)
        // Nothing moves until the player starts the game.
        assertFalse(before.canAim)
        assertFalse(before.isComputerThinking)

        viewModel.onStart()
        val shot = withTimeout(10_000) { viewModel.uiState.first { it.isBallsMoving } }
        assertEquals(EIGHT_BALL_COMPUTER, shot.currentPlayer)
        assertFalse(shot.canAim)
        // Choices are locked once the game is under way.
        viewModel.onFirstTurnChosen(FirstTurn.YOU)
        assertEquals(FirstTurn.COMPUTER, viewModel.uiState.value.firstTurn)
    }

    @Test
    fun `when you break, the break is yours and the groups stay open`() {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.canAim)
        viewModel.onStart()
        val state = viewModel.uiState.value
        assertEquals(EIGHT_BALL_HUMAN, state.currentPlayer)
        assertTrue(state.canAim)
        assertTrue(state.match.isBreak)
        assertTrue(state.playerGroups.isEmpty())
    }

    @Test
    fun `playing again keeps the breaker and goes back to the setup`() {
        val viewModel = viewModel()
        viewModel.onFirstTurnChosen(FirstTurn.COMPUTER)
        viewModel.onStart()
        viewModel.onPlayAgain()
        val state = viewModel.uiState.value
        assertEquals(FirstTurn.COMPUTER, state.firstTurn)
        assertEquals(EIGHT_BALL_COMPUTER, state.currentPlayer)
        assertFalse(state.started)
    }
}
