package com.example.playwin.feature.game.bingo

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme

@Composable
fun BingoRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: BingoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    BingoScreen(
        uiState = uiState,
        onBack = onBack,
        onCallNumber = viewModel::onCallNumber,
        onDaub = viewModel::onDaub,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BingoScreen(
    uiState: BingoUiState,
    onBack: () -> Unit,
    onCallNumber: () -> Unit,
    onDaub: (row: Int, col: Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Bingo", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                uiState.hasWon -> StatusBanner(text = "Bingo! Claiming your coupon…")
                uiState.isBoardExhausted -> StatusBanner(text = "All numbers called - no bingo this round.")
                else -> CurrentCallBanner(uiState)
            }

            CalledNumbersRow(uiState.calledNumbers, modifier = Modifier.padding(top = 12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrimaryButton(
                    text = "Call number",
                    onClick = onCallNumber,
                    enabled = !uiState.hasWon && !uiState.isResolving && !uiState.isBoardExhausted
                )
                if (uiState.isResolving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            BingoCard(
                uiState = uiState,
                onDaub = onDaub,
                modifier = Modifier.padding(top = 20.dp)
            )

            if (uiState.hasWon || uiState.isBoardExhausted) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

@Composable
private fun CurrentCallBanner(uiState: BingoUiState, modifier: Modifier = Modifier) {
    val text = uiState.currentCall?.let { "Current call: ${bingoLetterFor(it)}-$it" }
        ?: "Tap \"Call number\" to start"
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier.fillMaxWidth())
}

@Composable
private fun StatusBanner(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier.fillMaxWidth())
}

@Composable
private fun CalledNumbersRow(calledNumbers: List<Int>, modifier: Modifier = Modifier) {
    if (calledNumbers.isEmpty()) {
        Text(
            text = "No numbers called yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth()
        )
        return
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(calledNumbers.asReversed()) { number ->
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${bingoLetterFor(number)}-$number",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun BingoCard(
    uiState: BingoUiState,
    onDaub: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val canDaub = !uiState.hasWon && !uiState.isResolving

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            BINGO_LETTERS.forEach { letter ->
                Text(
                    text = letter,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        for (row in 0 until BINGO_SIZE) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until BINGO_SIZE) {
                    val isFree = row == BINGO_FREE_ROW && col == BINGO_FREE_COL
                    val number = uiState.card[row][col]
                    val isDaubed = uiState.daubed[row][col]
                    val isCallable = isFree || number in uiState.calledNumbers

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isDaubed -> MaterialTheme.colorScheme.tertiary
                                    isCallable -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                            .clickable(enabled = canDaub && isCallable && !isDaubed) { onDaub(row, col) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isFree) "FREE" else number.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isDaubed) FontWeight.Bold else FontWeight.Normal,
                            color = if (isDaubed) {
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
private fun BingoScreenPreview() {
    PlayWinTheme {
        BingoScreen(
            uiState = BingoUiState(),
            onBack = {},
            onCallNumber = {},
            onDaub = { _, _ -> },
            onPlayAgain = {}
        )
    }
}
