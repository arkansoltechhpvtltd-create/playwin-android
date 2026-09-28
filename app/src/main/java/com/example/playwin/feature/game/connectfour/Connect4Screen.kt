package com.example.playwin.feature.game.connectfour

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.feature.game.connectfour.online.Connect4OnlineRoute

private enum class Connect4Mode { COMPUTER, ONLINE }

/** Entry point for Connect 4: play the computer, or get paired with a random online player. */
@Composable
fun Connect4Route(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<Connect4Mode?>(null) }
    when (mode) {
        null -> Connect4ModeSelectScreen(
            onBack = onBack,
            onSelectComputer = { mode = Connect4Mode.COMPUTER },
            onSelectOnline = { mode = Connect4Mode.ONLINE }
        )
        Connect4Mode.COMPUTER -> Connect4ComputerRoute(onBack = { mode = null }, onWin = onWin)
        Connect4Mode.ONLINE -> Connect4OnlineRoute(
            onBack = { mode = null },
            onWin = onWin,
            onPlayComputer = { mode = Connect4Mode.COMPUTER }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Connect4ModeSelectScreen(
    onBack: () -> Unit,
    onSelectComputer: () -> Unit,
    onSelectOnline: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Connect 4", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "How do you want to play?", style = MaterialTheme.typography.titleLarge)
            PrimaryButton(text = "Play vs Computer", onClick = onSelectComputer, modifier = Modifier.padding(top = 24.dp))
            PrimaryButton(text = "Find a random player", onClick = onSelectOnline, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun Connect4ComputerRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: Connect4ViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    Connect4Screen(
        uiState = uiState,
        onBack = onBack,
        onColumnClick = viewModel::onColumnClick,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Connect4Screen(
    uiState: Connect4UiState,
    onBack: () -> Unit,
    onColumnClick: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Connect 4 · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                uiState.isDraw -> StatusBanner(text = "It's a draw!")
                uiState.winner == CONNECT4_HUMAN -> StatusBanner(text = "You win! Claiming your coupon…")
                uiState.winner != null -> StatusBanner(text = "The computer wins this round")
                else -> TurnBanner(uiState)
            }

            Connect4Board(
                board = uiState.board,
                enabled = uiState.currentPlayer == CONNECT4_HUMAN && !uiState.isGameOver && !uiState.isResolving,
                onColumnClick = onColumnClick,
                modifier = Modifier.padding(top = 16.dp)
            )

            if (uiState.isGameOver && uiState.winner != CONNECT4_HUMAN) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

@Composable
private fun TurnBanner(uiState: Connect4UiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = (if (uiState.currentPlayer == CONNECT4_HUMAN) "Your turn" else "Computer is thinking…") + if (uiState.lastTurnTimedOut) " (previous turn timed out)" else "",
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = { uiState.secondsLeft / CONNECT4_TURN_SECONDS.toFloat() },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Text(text = "${uiState.secondsLeft}s", style = MaterialTheme.typography.labelLarge)
            if (uiState.isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(16.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier.fillMaxWidth())
}

/** Shared by both the vs-Computer and online Connect 4 screens. */
@Composable
fun Connect4Board(
    board: List<List<Int>>,
    enabled: Boolean,
    onColumnClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cols = if (board.isNotEmpty()) board[0].size else 0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (col in 0 until cols) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled) { onColumnClick(col) },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (row in board.indices) {
                    val cell = board[row][col]
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(
                                when (cell) {
                                    1 -> MaterialTheme.colorScheme.tertiary
                                    2 -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun Connect4ScreenPreview() {
    PlayWinTheme {
        Connect4Screen(
            uiState = Connect4UiState(),
            onBack = {},
            onColumnClick = {},
            onPlayAgain = {}
        )
    }
}
