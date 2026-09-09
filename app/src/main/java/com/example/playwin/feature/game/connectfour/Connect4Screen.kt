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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.theme.PlayWinTheme

@Composable
fun Connect4Route(
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
        topBar = {
            TopAppBar(
                title = { Text("Connect 4") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
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
                uiState.winner != null -> StatusBanner(text = "Player ${uiState.winner} wins! Claiming your coupon…")
                else -> TurnBanner(uiState)
            }

            Board(
                board = uiState.board,
                enabled = uiState.winner == null && !uiState.isDraw && !uiState.isResolving,
                onColumnClick = onColumnClick,
                modifier = Modifier.padding(top = 16.dp)
            )

            if (uiState.isDraw) {
                Button(onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp)) {
                    Text("Play again")
                }
            }
        }
    }
}

@Composable
private fun TurnBanner(uiState: Connect4UiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Player ${uiState.currentPlayer}'s turn" + if (uiState.lastTurnTimedOut) " (previous turn timed out)" else "",
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

@Composable
private fun Board(
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
            .background(MaterialTheme.colorScheme.primary)
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
