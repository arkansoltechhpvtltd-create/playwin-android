package com.example.playwin.feature.game.carrom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import kotlinx.coroutines.isActive

@Composable
fun CarromRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: CarromViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    // Drives the physics simulation from the screen's own frame clock, independent
    // of the 20s turn timer (which lives in the ViewModel and simply pauses
    // while isPiecesMoving is true).
    LaunchedEffect(Unit) {
        var lastFrameTimeNanos = 0L
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTimeNanos != 0L) {
                    val dtSeconds = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
                    viewModel.onPhysicsTick(dtSeconds)
                }
                lastFrameTimeNanos = frameTimeNanos
            }
        }
    }

    CarromScreen(
        uiState = uiState,
        onBack = onBack,
        onShoot = viewModel::onShoot,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarromScreen(
    uiState: CarromUiState,
    onBack: () -> Unit,
    onShoot: (direction: Vec2, power: Float) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Carrom") },
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
                uiState.isDraw -> StatusBanner(text = "It's a draw! Scores: ${uiState.scores[1]} - ${uiState.scores[2]}")
                uiState.winner != null -> StatusBanner(
                    text = "Player ${uiState.winner} wins ${uiState.scores[1]}-${uiState.scores[2]}! Claiming your coupon…"
                )
                else -> TurnBanner(uiState)
            }

            CarromBoardView(
                uiState = uiState,
                onShoot = onShoot,
                modifier = Modifier.padding(top = 16.dp)
            )

            ScoreLegend(uiState = uiState, modifier = Modifier.padding(top = 12.dp))

            if (uiState.winner != null || uiState.isDraw) {
                Button(onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp)) {
                    Text("Play again")
                }
            }
        }
    }
}

@Composable
private fun TurnBanner(uiState: CarromUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val color = uiState.playerColors[uiState.currentPlayer]
        val colorLabel = when (color) {
            CoinColor.WHITE -> " (white)"
            CoinColor.BLACK -> " (black)"
            else -> ""
        }
        val suffix = buildString {
            if (uiState.lastTurnTimedOut) append(" (previous turn timed out)")
            if (uiState.fouled) append(" (foul on previous shot)")
        }
        Text(
            text = "Player ${uiState.currentPlayer}'s turn$colorLabel$suffix",
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = { uiState.secondsLeft / CARROM_TURN_SECONDS.toFloat() },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Text(text = "${uiState.secondsLeft}s", style = MaterialTheme.typography.labelLarge)
            if (uiState.isPiecesMoving || uiState.isResolving) {
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
private fun ScoreLegend(uiState: CarromUiState, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        val p1Color = uiState.playerColors[1]
        val text = if (p1Color == null) {
            "Board open - first pot picks your color · Score ${uiState.scores[1]} - ${uiState.scores[2]}"
        } else {
            "Player 1: ${p1Color.name.lowercase()} · Player 2: ${uiState.playerColors[2]?.name?.lowercase()} · " +
                "Score ${uiState.scores[1]} - ${uiState.scores[2]}"
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val BoardTan = Color(0xFFE7C9A0)
private val BoardBorder = Color(0xFF3E2412)
private val PocketBlack = Color(0xFF0A0A0A)
private val WhiteCoin = Color(0xFFF7F3E8)
private val BlackCoin = Color(0xFF1A1A1A)
private val QueenRed = Color(0xFFC62828)
private val StrikerColor = Color(0xFFFFFFFF)
private val StrikerRing = Color(0xFFD32F2F)

@Composable
private fun CarromBoardView(
    uiState: CarromUiState,
    onShoot: (Vec2, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    val scale = if (canvasSize.width > 0) canvasSize.width / CarromBoard.SIZE else 0f
    val striker = uiState.coins.firstOrNull { it.id == STRIKER_ID && !it.pocketed }
    val strikerScreenPos = striker?.let { Offset(it.position.x * scale, it.position.y * scale) }

    val maxDragPx = canvasSize.width * 0.4f
    val minDragPx = canvasSize.width * 0.02f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onSizeChanged { canvasSize = it }
            .pointerInput(uiState.canAim) {
                if (!uiState.canAim) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset -> dragCurrent = offset },
                    onDrag = { change, _ -> dragCurrent = change.position },
                    onDragEnd = {
                        val start = strikerScreenPos
                        val end = dragCurrent
                        if (start != null && end != null) {
                            val pullDistance = (end - start).getDistance()
                            if (pullDistance > minDragPx && maxDragPx > 0f) {
                                val direction = Vec2(start.x - end.x, start.y - end.y).normalizedOrZero()
                                val power = (pullDistance / maxDragPx).coerceIn(0f, 1f)
                                onShoot(direction, power)
                            }
                        }
                        dragCurrent = null
                    },
                    onDragCancel = { dragCurrent = null }
                )
            }
    ) {
        val s = size.width / CarromBoard.SIZE
        fun toScreen(v: Vec2) = Offset(v.x * s, v.y * s)

        drawRect(color = BoardTan, size = size)
        drawRect(color = BoardBorder, size = size, style = Stroke(width = 14f * s))
        drawCircle(
            color = BoardBorder.copy(alpha = 0.6f),
            radius = CarromBoard.COIN_RADIUS * 4.4f * s + CarromBoard.COIN_RADIUS * s,
            center = toScreen(Vec2(CarromBoard.SIZE / 2f, CarromBoard.SIZE / 2f)),
            style = Stroke(width = 2f * s)
        )

        val pocketRadius = CarromBoard.POCKET_RADIUS * s
        CarromBoard.corners.forEach { corner ->
            drawCircle(color = PocketBlack, radius = pocketRadius, center = toScreen(corner))
        }

        uiState.coins.filterNot { it.pocketed }.forEach { coin ->
            val color = when {
                coin.id == STRIKER_ID -> StrikerColor
                coinColorOf(coin.id) == CoinColor.WHITE -> WhiteCoin
                coinColorOf(coin.id) == CoinColor.BLACK -> BlackCoin
                else -> QueenRed
            }
            val radius = CarromBoard.radiusOf(coin.id) * s
            drawCircle(color = color, radius = radius, center = toScreen(coin.position))
            if (coin.id == STRIKER_ID) {
                drawCircle(color = StrikerRing, radius = radius * 0.6f, center = toScreen(coin.position), style = Stroke(width = 3f * s))
                drawCircle(color = Color.Black.copy(alpha = 0.4f), radius = radius, center = toScreen(coin.position), style = Stroke(width = 2f * s))
            } else {
                drawCircle(color = Color.Black.copy(alpha = 0.25f), radius = radius, center = toScreen(coin.position), style = Stroke(width = 1.5f))
            }
        }

        val start = strikerScreenPos
        val end = dragCurrent
        if (start != null && end != null && uiState.canAim) {
            val pullDistance = (end - start).getDistance()
            val power = if (maxDragPx > 0f) (pullDistance / maxDragPx).coerceIn(0f, 1f) else 0f
            val direction = Vec2(start.x - end.x, start.y - end.y).normalizedOrZero()
            val aimLineLength = 70f + power * 200f
            val aimLineEnd = Offset(start.x + direction.x * aimLineLength, start.y + direction.y * aimLineLength)
            drawLine(color = Color.Black.copy(alpha = 0.6f), start = start, end = end, strokeWidth = 3f)
            drawLine(color = QueenRed.copy(alpha = 0.85f), start = start, end = aimLineEnd, strokeWidth = 4f)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun CarromScreenPreview() {
    PlayWinTheme {
        CarromScreen(
            uiState = CarromUiState(),
            onBack = {},
            onShoot = { _, _ -> },
            onPlayAgain = {}
        )
    }
}
