package com.example.playwin.feature.game.wordhunt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme

@Composable
fun WordHuntRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: WordHuntViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    WordHuntScreen(
        uiState = uiState,
        onBack = onBack,
        onCellTap = viewModel::onCellTap,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordHuntScreen(
    uiState: WordHuntUiState,
    onBack: () -> Unit,
    onCellTap: (row: Int, col: Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Word Hunt", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                uiState.hasWon -> StatusBanner(text = "All words found! Claiming your coupon…")
                uiState.hasLost -> StatusBanner(text = "Time's up! Better luck next time.")
                else -> TimerBanner(uiState)
            }

            WordList(uiState, modifier = Modifier.padding(top = 12.dp))

            LetterGrid(
                uiState = uiState,
                onCellTap = onCellTap,
                modifier = Modifier.padding(top = 16.dp)
            )

            if (uiState.hasWon || uiState.hasLost) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

@Composable
private fun TimerBanner(uiState: WordHuntUiState, modifier: Modifier = Modifier) {
    val isWarning = uiState.secondsLeft <= WORD_HUNT_TIME_WARNING_SECONDS
    val color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Time: ${uiState.secondsLeft}s",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        if (uiState.isResolving) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = 10.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun StatusBanner(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier.fillMaxWidth())
}

@Composable
private fun WordList(uiState: WordHuntUiState, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        uiState.words.forEach { placed ->
            val isFound = placed.word in uiState.foundWords
            Text(
                text = placed.word,
                style = MaterialTheme.typography.labelLarge,
                textDecoration = if (isFound) TextDecoration.LineThrough else TextDecoration.None,
                color = if (isFound) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun LetterGrid(
    uiState: WordHuntUiState,
    onCellTap: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = !uiState.hasWon && !uiState.hasLost && !uiState.isResolving

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (row in uiState.grid.indices) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                for (col in uiState.grid[row].indices) {
                    val isSelected = uiState.firstSelection == row to col
                    val isFoundCell = row to col in uiState.foundCells

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    isFoundCell -> MaterialTheme.colorScheme.tertiary
                                    isSelected -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                            .clickable(enabled = enabled) { onCellTap(row, col) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.grid[row][col].toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isFoundCell) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            color = if (isFoundCell) {
                                MaterialTheme.colorScheme.onTertiary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordHuntScreenPreview() {
    PlayWinTheme {
        WordHuntScreen(
            uiState = WordHuntUiState(),
            onBack = {},
            onCellTap = { _, _ -> },
            onPlayAgain = {}
        )
    }
}
