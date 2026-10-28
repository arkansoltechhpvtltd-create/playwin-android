package com.example.playwin.feature.game.chinesecheckers

import com.example.playwin.core.data.mock.MockCouponsRepository
import com.example.playwin.core.data.mock.MockGameEngineRepository
import com.example.playwin.core.data.mock.MockRewardsRepository
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersBoard
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersDifficulty
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersRules
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
class ChineseCheckersSideTest {

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

    private fun viewModel() = ChineseCheckersViewModel(MockGameEngineRepository(), MockRewardsRepository(), MockCouponsRepository())

    @Test
    fun `playing Ruby, your pegs are the top point's and you still move first`() = runBlocking {
        val viewModel = viewModel()
        viewModel.onDifficultyChosen(ChineseCheckersDifficulty.EASY)
        viewModel.onColorChosen(PegColor.RUBY)
        val before = viewModel.uiState.value
        assertEquals(2, before.humanPlayer)
        assertEquals(1, before.computerPlayer)
        assertEquals(ChineseCheckersBoard.home(2), before.position.pegsOf(before.humanPlayer).toSet())
        assertTrue(before.isHumanTurn)
        // Nothing can be moved until the player starts the game.
        assertFalse(before.canPlay)

        viewModel.onStart()
        assertTrue(viewModel.uiState.value.canPlay)

        // A peg of the computer's (Gold) can't be picked up.
        viewModel.onHoleTap(ChineseCheckersBoard.home(1).first())
        assertNull(viewModel.uiState.value.selected)

        // One of yours can, and moving it hands the turn to the computer, which answers.
        val peg = before.position.pegsOf(2).first { ChineseCheckersRules.movesFrom(before.position, it).isNotEmpty() }
        viewModel.onHoleTap(peg)
        val selected = viewModel.uiState.value
        assertEquals(peg, selected.selected)
        viewModel.onHoleTap(selected.legalTargets.first())
        val replied = withTimeout(10_000) { viewModel.uiState.first { it.moveCount == 2 && !it.isResolving } }
        assertTrue(replied.isHumanTurn)
        assertEquals(ChineseCheckersBoard.PEGS_PER_PLAYER, replied.position.pegsOf(1).size)
    }

    @Test
    fun `playing Gold, you start from the bottom point and move first`() {
        val viewModel = viewModel()
        val state = viewModel.uiState.value
        assertEquals(PegColor.GOLD, state.humanColor)
        assertEquals(1, state.humanPlayer)
        assertEquals(1, state.position.sideToMove)
        viewModel.onStart()
        assertTrue(viewModel.uiState.value.canPlay)
        assertFalse(viewModel.uiState.value.isComputerThinking)
    }

    @Test
    fun `the colour can't be changed once the game has started`() {
        val viewModel = viewModel()
        viewModel.onColorChosen(PegColor.RUBY)
        viewModel.onStart()
        viewModel.onColorChosen(PegColor.GOLD)
        assertEquals(PegColor.RUBY, viewModel.uiState.value.humanColor)
    }
}
