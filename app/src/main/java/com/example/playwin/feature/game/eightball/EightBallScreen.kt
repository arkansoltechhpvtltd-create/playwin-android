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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.playwin.core.domain.eightball.Ball
import com.example.playwin.core.domain.eightball.BallGroup
import com.example.playwin.core.domain.eightball.CUE_BALL_ID
import com.example.playwin.core.domain.eightball.EightBallTable
import com.example.playwin.core.domain.eightball.Vec2
import com.example.playwin.core.domain.eightball.ballGroupOf
import com.example.playwin.feature.game.PhysicsFrameClock
import com.example.playwin.feature.game.eightball.online.EightBallOnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode

/** Entry point for 8-Ball: play the computer, or get paired with a random online player. */
@Composable
fun EightBallRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<PlayMode?>(null) }
    when (mode) {
        null -> GameModeSelectScreen(title = "8-Ball", onBack = onBack, onSelectMode = { mode = it })
        PlayMode.COMPUTER -> EightBallComputerRoute(onBack = { mode = null }, onWin = onWin)
        PlayMode.ONLINE -> EightBallOnlineRoute(
            onBack = { mode = null },
            onWin = onWin,
            onPlayComputer = { mode = PlayMode.COMPUTER }
        )
    }
}

@Composable
private fun EightBallComputerRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: EightBallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    PhysicsFrameClock(onTick = viewModel::onPhysicsTick)

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
        topBar = { PwTopBar(title = "8-Ball · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val foul = if (uiState.endedOnFoul) " on a foul" else ""
            when (uiState.winner) {
                EIGHT_BALL_HUMAN -> StatusBanner(text = "You win$foul! Claiming your coupon…")
                EIGHT_BALL_COMPUTER -> StatusBanner(text = "The computer wins$foul")
                else -> TurnBanner(
                    text = when {
                        uiState.isComputerThinking -> "Computer is lining up a shot…"
                        uiState.currentPlayer == EIGHT_BALL_HUMAN -> "Your shot - drag back from the cue ball"
                        else -> "Computer's shot"
                    } + buildString {
                        if (uiState.lastTurnTimedOut) append(" (previous turn timed out)")
                        if (uiState.fouled) append(" (scratch on previous shot)")
                    },
                    secondsLeft = uiState.secondsLeft.takeIf { uiState.currentPlayer == EIGHT_BALL_HUMAN },
                    isBusy = uiState.isBallsMoving || uiState.isResolving || uiState.isComputerThinking
                )
            }

            PoolTable(
                balls = uiState.balls,
                canAim = uiState.canAim,
                onShoot = onShoot,
                modifier = Modifier.padding(top = 16.dp)
            )

            GroupLegend(myGroup = uiState.playerGroups[EIGHT_BALL_HUMAN], modifier = Modifier.padding(top = 12.dp))

            if (uiState.winner == EIGHT_BALL_COMPUTER) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

@Composable
fun TurnBanner(text: String, secondsLeft: Int?, isBusy: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (secondsLeft != null) {
                LinearProgressIndicator(
                    progress = { secondsLeft / EIGHT_BALL_TURN_SECONDS.toFloat() },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Text(text = "${secondsLeft}s", style = MaterialTheme.typography.labelLarge)
            }
            if (isBusy) {
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
fun GroupLegend(myGroup: BallGroup?, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        val text = when (myGroup) {
            null -> "Table open - first pot picks your group"
            else -> "You: ${myGroup.name.lowercase()} · then sink the 8"
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

/** The table. Drag back from the cue ball to aim; release to shoot. */
@Composable
fun PoolTable(
    balls: List<Ball>,
    canAim: Boolean,
    onShoot: (Vec2, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    val scale = if (canvasSize.width > 0) canvasSize.width / EightBallTable.WIDTH else 0f
    val cueBall = balls.firstOrNull { it.id == CUE_BALL_ID && !it.pocketed }
    val cueScreenPos = cueBall?.let { Offset(it.position.x * scale, it.position.y * scale) }

    val maxDragPx = canvasSize.width * 0.4f
    val minDragPx = canvasSize.width * 0.02f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .onSizeChanged { canvasSize = it }
            .pointerInput(canAim, cueScreenPos) {
                if (!canAim) return@pointerInput
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

        balls.filterNot { it.pocketed }.forEach { ball ->
            drawBall(ball, toScreen(ball.position), EightBallTable.BALL_RADIUS * s)
        }

        val start = cueScreenPos
        val end = dragCurrent
        if (start != null && end != null && canAim) {
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
