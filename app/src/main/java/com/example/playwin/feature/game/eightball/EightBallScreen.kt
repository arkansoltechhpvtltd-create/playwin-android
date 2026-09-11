package com.example.playwin.feature.game.eightball

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import kotlinx.coroutines.isActive

@Composable
fun EightBallRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: EightBallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    // Drives the physics simulation from the screen's own frame clock, independent
    // of the 20s turn timer (which lives in the ViewModel and simply pauses
    // while isBallsMoving is true).
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

    EightBallScreen(
        uiState = uiState,
        onBack = onBack,
        onShoot = viewModel::onShoot,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EightBallScreen(
    uiState: EightBallUiState,
    onBack: () -> Unit,
    onShoot: (direction: Vec2, power: Float) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "8-Ball", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                uiState.winner != null -> StatusBanner(
                    text = if (uiState.fouled) {
                        "Player ${uiState.winner} wins on a foul! Claiming your coupon…"
                    } else {
                        "Player ${uiState.winner} wins! Claiming your coupon…"
                    }
                )
                else -> TurnBanner(uiState)
            }

            PoolTable(
                uiState = uiState,
                onShoot = onShoot,
                modifier = Modifier.padding(top = 16.dp)
            )

            GroupLegend(uiState = uiState, modifier = Modifier.padding(top = 12.dp))

            if (uiState.winner != null) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

@Composable
private fun TurnBanner(uiState: EightBallUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val group = uiState.playerGroups[uiState.currentPlayer]
        val groupLabel = when (group) {
            BallGroup.SOLIDS -> " (solids)"
            BallGroup.STRIPES -> " (stripes)"
            null -> ""
        }
        Text(
            text = "Player ${uiState.currentPlayer}'s turn$groupLabel" +
                if (uiState.lastTurnTimedOut) " (previous turn timed out)" else "",
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = { uiState.secondsLeft / EIGHT_BALL_TURN_SECONDS.toFloat() },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Text(text = "${uiState.secondsLeft}s", style = MaterialTheme.typography.labelLarge)
            if (uiState.isBallsMoving || uiState.isResolving) {
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
private fun GroupLegend(uiState: EightBallUiState, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        val p1Group = uiState.playerGroups[1]
        val p2Group = uiState.playerGroups[2]
        val text = when {
            p1Group == null -> "Table open - first pot picks your group"
            else -> "Player 1: ${p1Group.name.lowercase()} · Player 2: ${p2Group?.name?.lowercase()}"
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val FeltGreen = Color(0xFF1B5E3A)
private val RailBrown = Color(0xFF5D3A1A)
private val PocketBlack = Color(0xFF0A0A0A)

private val ballColors = mapOf(
    0 to Color(0xFFF5F5F0),
    1 to Color(0xFFFFC400),
    2 to Color(0xFF1E5FFF),
    3 to Color(0xFFE4241C),
    4 to Color(0xFF7B1FA2),
    5 to Color(0xFFFF7A1A),
    6 to Color(0xFF1E8A3C),
    7 to Color(0xFF8C1B1B),
    8 to Color(0xFF141414)
)

private fun colorForBall(id: Int): Color = ballColors[id] ?: ballColors.getValue(id - 8)

@Composable
private fun PoolTable(
    uiState: EightBallUiState,
    onShoot: (Vec2, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    val scale = if (canvasSize.width > 0) canvasSize.width / EightBallTable.WIDTH else 0f
    val cueBall = uiState.balls.firstOrNull { it.id == 0 && !it.pocketed }
    val cueScreenPos = cueBall?.let { Offset(it.position.x * scale, it.position.y * scale) }

    val maxDragPx = canvasSize.width * 0.4f
    val minDragPx = canvasSize.width * 0.02f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .onSizeChanged { canvasSize = it }
            .pointerInput(uiState.canAim) {
                if (!uiState.canAim) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset -> dragCurrent = offset },
                    onDrag = { change, _ -> dragCurrent = change.position },
                    onDragEnd = {
                        val start = cueScreenPos
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
        val s = size.width / EightBallTable.WIDTH
        fun toScreen(v: Vec2) = Offset(v.x * s, v.y * s)

        drawRect(color = FeltGreen, size = size)
        drawRect(color = RailBrown, size = size, style = Stroke(width = 10f * s))

        val pocketRadius = EightBallTable.POCKET_RADIUS * s
        EightBallTable.corners.forEach { corner ->
            drawCircle(color = PocketBlack, radius = pocketRadius, center = toScreen(corner))
        }

        uiState.balls.filterNot { it.pocketed }.forEach { ball ->
            drawBall(ball, toScreen(ball.position), EightBallTable.BALL_RADIUS * s)
        }

        val start = cueScreenPos
        val end = dragCurrent
        if (start != null && end != null && uiState.canAim) {
            val pullDistance = (end - start).getDistance()
            val power = if (maxDragPx > 0f) (pullDistance / maxDragPx).coerceIn(0f, 1f) else 0f
            val direction = Vec2(start.x - end.x, start.y - end.y).normalizedOrZero()
            val aimLineLength = 80f + power * 220f
            val aimLineEnd = Offset(
                start.x + direction.x * aimLineLength,
                start.y + direction.y * aimLineLength
            )
            drawLine(color = Color.White.copy(alpha = 0.7f), start = start, end = end, strokeWidth = 3f)
            drawLine(color = Color.Red.copy(alpha = 0.8f), start = start, end = aimLineEnd, strokeWidth = 4f)
        }
    }
}

private fun DrawScope.drawBall(ball: Ball, center: Offset, radius: Float) {
    val group = ballGroupOf(ball.id)
    val color = colorForBall(ball.id)

    if (group == BallGroup.STRIPES) {
        drawCircle(color = Color(0xFFF5F5F0), radius = radius, center = center)
        val path = Path().apply {
            addOval(Rect(center = center, radius = radius))
        }
        clipPath(path) {
            drawRect(
                color = color,
                topLeft = Offset(center.x - radius, center.y - radius * 0.42f),
                size = Size(radius * 2f, radius * 0.84f)
            )
        }
    } else {
        drawCircle(color = color, radius = radius, center = center)
    }
    drawCircle(color = Color.Black.copy(alpha = 0.25f), radius = radius, center = center, style = Stroke(width = 1.5f))
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun EightBallScreenPreview() {
    PlayWinTheme {
        EightBallScreen(
            uiState = EightBallUiState(),
            onBack = {},
            onShoot = { _, _ -> },
            onPlayAgain = {}
        )
    }
}
