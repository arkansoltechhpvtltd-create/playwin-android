package com.example.playwin.feature.game.twenty48

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import kotlin.math.abs

@Composable
fun Twenty48Route(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: Twenty48ViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    Twenty48Screen(
        uiState = uiState,
        onBack = onBack,
        onSwipe = viewModel::onSwipe,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Twenty48Screen(
    uiState: Twenty48UiState,
    onBack: () -> Unit,
    onSwipe: (SwipeDirection) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            PwTopBar(
                title = "2048",
                onBack = onBack,
                actions = {
                    Text(
                        text = formatElapsed(uiState.elapsedSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 16.dp)
                    )
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
            val statusText = when {
                uiState.hasWon -> "You reached 2048! Claiming your coupon…"
                uiState.isGameOver -> "No more moves left"
                else -> "Swipe to merge tiles"
            }
            Text(text = statusText, style = MaterialTheme.typography.titleMedium)

            Board(
                board = uiState.board,
                enabled = !uiState.hasWon && !uiState.isGameOver,
                onSwipe = onSwipe,
                modifier = Modifier.padding(top = 16.dp)
            )

            if (uiState.isGameOver) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun Board(
    board: List<List<Int>>,
    enabled: Boolean,
    onSwipe: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(6.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var totalDrag = Offset.Zero
                detectDragGestures(
                    onDragStart = { totalDrag = Offset.Zero },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        resolveSwipeDirection(totalDrag)?.let(onSwipe)
                    }
                )
            }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_SIZE),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize()
        ) {
            items(board.flatten()) { value ->
                Tile(value = value, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
private fun Tile(value: Int, modifier: Modifier = Modifier) {
    val background = tileColor(value)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (value != 0) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (value <= 4) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun tileColor(value: Int) = when (value) {
    0 -> MaterialTheme.colorScheme.surface
    2 -> MaterialTheme.colorScheme.surfaceVariant
    4 -> MaterialTheme.colorScheme.tertiaryContainer
    8 -> MaterialTheme.colorScheme.tertiary
    16 -> MaterialTheme.colorScheme.secondary
    32 -> MaterialTheme.colorScheme.secondary
    64 -> MaterialTheme.colorScheme.primary
    128, 256, 512 -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.primary
}

private fun resolveSwipeDirection(offset: Offset): SwipeDirection? {
    val threshold = 40f
    if (abs(offset.x) < threshold && abs(offset.y) < threshold) return null
    return if (abs(offset.x) > abs(offset.y)) {
        if (offset.x > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
    } else {
        if (offset.y > 0) SwipeDirection.DOWN else SwipeDirection.UP
    }
}

@Preview
@Composable
private fun Twenty48ScreenPreview() {
    PlayWinTheme {
        Twenty48Screen(
            uiState = Twenty48UiState(board = Twenty48Board.emptyBoardWithStartingTiles()),
            onBack = {},
            onSwipe = {},
            onPlayAgain = {}
        )
    }
}
