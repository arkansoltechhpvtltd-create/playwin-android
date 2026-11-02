package com.example.playwin.feature.game.chess

import com.example.playwin.core.data.mock.MockCouponsRepository
import com.example.playwin.core.data.mock.MockGameEngineRepository
import com.example.playwin.core.data.mock.MockRewardsRepository
import com.example.playwin.core.domain.chess.ChessDifficulty
import com.example.playwin.core.domain.chess.PieceColor
import com.example.playwin.core.domain.chess.Square
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
class ChessSideTest {

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

    private fun viewModel() = ChessViewModel(MockGameEngineRepository(), MockRewardsRepository(MockCouponsRepository()))

    @Test
    fun `playing Black, the computer makes White's first move`() = runBlocking {
        val viewModel = viewModel()
        viewModel.onDifficultyChosen(ChessDifficulty.EASY)
        viewModel.onSideChosen(PieceColor.BLACK)
        val before = viewModel.uiState.value
        assertEquals(PieceColor.BLACK, before.humanColor)
        // Nothing is played until the player starts the game.
        assertTrue(before.awaitingStart)
        assertFalse(before.isHumanTurn)

        viewModel.onStart()
        val after = withTimeout(10_000) { viewModel.uiState.first { it.moveHistory.isNotEmpty() && !it.isResolving } }

        assertEquals(1, after.moveHistory.size)
        assertEquals(PieceColor.WHITE, after.moveHistory.single().mover)
        assertEquals(PieceColor.BLACK, after.position.sideToMove)
        assertTrue(after.isHumanTurn)
        assertFalse(after.canChooseTimeControl)
    }

    @Test
    fun `playing Black, the player moves the black pieces after the computer opens`() = runBlocking {
        val viewModel = viewModel()
        viewModel.onDifficultyChosen(ChessDifficulty.EASY)
        viewModel.onSideChosen(PieceColor.BLACK)
        viewModel.onStart()
        withTimeout(10_000) { viewModel.uiState.first { it.isHumanTurn && !it.isResolving } }

        // A black knight from g8: its moves are offered, and playing one hands the turn back.
        viewModel.onSquareTap(Square(7, 6))
        val selected = withTimeout(10_000) { viewModel.uiState.first { it.selectedSquare != null } }
        assertTrue(selected.legalTargets.isNotEmpty())
        viewModel.onSquareTap(selected.legalTargets.first())
        val replied = withTimeout(10_000) { viewModel.uiState.first { it.moveHistory.size == 2 } }
        assertEquals(PieceColor.BLACK, replied.moveHistory[1].mover)
    }

    @Test
    fun `playing White, nothing moves until the player does`() = runBlocking {
        val viewModel = viewModel()
        val state = viewModel.uiState.value
        assertEquals(PieceColor.WHITE, state.humanColor)
        assertFalse(state.awaitingStart)
        assertTrue(state.isHumanTurn)
        viewModel.onStart()
        assertTrue(viewModel.uiState.value.moveHistory.isEmpty())
        assertFalse(viewModel.uiState.value.isComputerThinking)
    }

    @Test
    fun `the side can't be changed once the game has started`() = runBlocking {
        val viewModel = viewModel()
        viewModel.onDifficultyChosen(ChessDifficulty.EASY)
        viewModel.onSideChosen(PieceColor.BLACK)
        viewModel.onStart()
        viewModel.onSideChosen(PieceColor.WHITE)
        assertEquals(PieceColor.BLACK, viewModel.uiState.value.humanColor)
    }
}
