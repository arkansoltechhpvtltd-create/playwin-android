package com.example.playwin.feature.game.carrom

import com.example.playwin.core.data.mock.MockCouponsRepository
import com.example.playwin.core.data.mock.MockGameEngineRepository
import com.example.playwin.core.data.mock.MockRewardsRepository
import com.example.playwin.core.domain.carrom.CarromBoard
import com.example.playwin.core.domain.carrom.CarromFoul
import com.example.playwin.core.domain.carrom.CarromRules
import com.example.playwin.core.domain.carrom.COINS_PER_COLOR
import com.example.playwin.core.domain.carrom.CoinColor
import com.example.playwin.core.domain.carrom.STRIKER_ID
import com.example.playwin.core.domain.carrom.coinColorOf
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
class CarromSideTest {

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

    private fun viewModel() = CarromViewModel(MockGameEngineRepository(), MockRewardsRepository(MockCouponsRepository()))

    @Test
    fun `the chosen colour is the player's from the start, the other the computer's`() {
        val viewModel = viewModel()
        viewModel.onColorChosen(CoinColor.BLACK)
        val state = viewModel.uiState.value
        assertEquals(CoinColor.BLACK, state.playerColors[CARROM_HUMAN])
        assertEquals(CoinColor.WHITE, state.playerColors[CARROM_COMPUTER])
    }

    @Test
    fun `the colour isn't decided by the first pot - potting the other colour first changes nothing`() {
        val match = carromMatchFor(CoinColor.BLACK, FirstTurn.YOU)
        val white = match.coins.first { it.id != STRIKER_ID && coinColorOf(it.id) == CoinColor.WHITE }.id
        val outcome = CarromRules.resolveShot(match, match.coins.map { if (it.id == white) it.copy(pocketed = true) else it }, listOf(white))

        assertEquals(CoinColor.BLACK, outcome.after.playerColors[CARROM_HUMAN])
        // A white coin is the computer's: +1 to it, and the turn passes.
        assertEquals(0, outcome.after.scores[CARROM_HUMAN])
        assertEquals(1, outcome.after.scores[CARROM_COMPUTER])
        assertEquals(CARROM_COMPUTER, outcome.after.currentPlayer)
    }

    @Test
    fun `the colour-keyed rules follow the player's chosen colour`() {
        val match = carromMatchFor(CoinColor.BLACK, FirstTurn.YOU)
        val black = match.coins.first { it.id != STRIKER_ID && coinColorOf(it.id) == CoinColor.BLACK }.id

        // Potting your own (black) coin: +1 to you, and you shoot again.
        val potted = CarromRules.resolveShot(match, match.coins.map { if (it.id == black) it.copy(pocketed = true) else it }, listOf(black))
        assertEquals(1, potted.after.scores[CARROM_HUMAN])
        assertEquals(CARROM_HUMAN, potted.after.currentPlayer)
        assertEquals(COINS_PER_COLOR - 1, potted.after.coinsLeft(CARROM_HUMAN))

        // Pocketing the striker next: the due is one of your black coins, back to the centre.
        val coins = potted.after.coins.map { if (it.id == STRIKER_ID) it.copy(pocketed = true) else it }
        val foul = CarromRules.resolveShot(potted.after, coins, listOf(STRIKER_ID))
        assertTrue(foul.fouled)
        assertEquals(CarromFoul.STRIKER, foul.foul)
        assertEquals(listOf(black), foul.returnedToCentre)
        assertEquals(0, foul.after.scores[CARROM_HUMAN])
    }

    @Test
    fun `when the computer breaks, it takes the first shot from its own baseline`() = runBlocking {
        val viewModel = viewModel()
        viewModel.onColorChosen(CoinColor.BLACK)
        viewModel.onFirstTurnChosen(FirstTurn.COMPUTER)
        val before = viewModel.uiState.value
        assertEquals(CARROM_COMPUTER, before.currentPlayer)
        assertEquals(CarromBoard.baselineFor(CARROM_COMPUTER).y, before.coins.first { it.id == STRIKER_ID }.position.y)
        // Nothing moves until the player starts the game.
        assertFalse(before.canAim)
        assertFalse(before.isComputerThinking)

        viewModel.onStart()
        val shot = withTimeout(10_000) { viewModel.uiState.first { it.isPiecesMoving } }
        assertEquals(CARROM_COMPUTER, shot.currentPlayer)
        assertFalse(shot.canAim)
        // Choices are locked once the game is under way.
        viewModel.onColorChosen(CoinColor.WHITE)
        assertEquals(CoinColor.BLACK, viewModel.uiState.value.humanColor)
    }

    @Test
    fun `when you break, the first shot is yours`() {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.canAim)
        viewModel.onStart()
        val state = viewModel.uiState.value
        assertEquals(CARROM_HUMAN, state.currentPlayer)
        assertTrue(state.canAim)
    }
}
