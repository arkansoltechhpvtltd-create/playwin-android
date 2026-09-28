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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.domain.carrom.CarromBoard
import com.example.playwin.core.domain.carrom.Coin
import com.example.playwin.core.domain.carrom.CoinColor
import com.example.playwin.core.domain.carrom.STRIKER_ID
import com.example.playwin.core.domain.carrom.Vec2
import com.example.playwin.core.domain.carrom.coinColorOf
import com.example.playwin.feature.game.carrom.online.CarromOnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
import kotlinx.coroutines.isActive

/** Entry point for Carrom: play the computer, or get paired with a random online player. */
@Composable
fun CarromRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<PlayMode?>(null) }
    when (mode) {
        null -> GameModeSelectScreen(title = "Carrom", onBack = onBack, onSelectMode = { mode = it })
        PlayMode.COMPUTER -> CarromComputerRoute(onBack = { mode = null }, onWin = onWin)
        PlayMode.ONLINE -> CarromOnlineRoute(
            onBack = { mode = null },
            onWin = onWin,
            onPlayComputer = { mode = PlayMode.COMPUTER }
        )
    }
}

@Composable
private fun CarromComputerRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: CarromViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    PhysicsFrameClock(onTick = viewModel::onPhysicsTick)

    CarromScreen(
        uiState = uiState,
        onBack = onBack,
        onShoot = viewModel::onShoot,
        onPlayAgain = viewModel::onPlayAgain
    )
}

/**
 * Drives a physics simulation from the screen's own frame clock, independent
 * of any turn timer: [onTick] gets the seconds elapsed since the last frame.
 */
@Composable
fun PhysicsFrameClock(onTick: (dtSeconds: Float) -> Unit) {
    val tick by rememberUpdatedState(onTick)
    LaunchedEffect(Unit) {
        var lastFrameTimeNanos = 0L
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTimeNanos != 0L) {
                    tick((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f)
                }
                lastFrameTimeNanos = frameTimeNanos
            }
        }
    }
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
        topBar = { PwTopBar(title = "Carrom · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val score = "${uiState.scores[CARROM_HUMAN]}-${uiState.scores[CARROM_COMPUTER]}"
            when {
                uiState.isDraw -> StatusBanner(text = "It's a draw! $score")
                uiState.winner == CARROM_HUMAN -> StatusBanner(text = "You win $score! Claiming your coupon…")
                uiState.winner == CARROM_COMPUTER -> StatusBanner(text = "The computer wins $score")
                else -> TurnBanner(
                    text = when {
                        uiState.isComputerThinking -> "Computer is lining up a shot…"
                        uiState.currentPlayer == CARROM_HUMAN -> "Your shot - drag back from the striker"
                        else -> "Computer's shot"
                    } + buildString {
                        if (uiState.lastTurnTimedOut) append(" (previous turn timed out)")
                        if (uiState.fouled) append(" (foul on previous shot)")
                    },
                    secondsLeft = uiState.secondsLeft.takeIf { uiState.currentPlayer == CARROM_HUMAN },
                    isBusy = uiState.isPiecesMoving || uiState.isResolving || uiState.isComputerThinking
                )
            }

            CarromBoardView(
                coins = uiState.coins,
                canAim = uiState.canAim,
                onShoot = onShoot,
                modifier = Modifier.padding(top = 16.dp)
            )

            ScoreLegend(
                myColor = uiState.playerColors[CARROM_HUMAN],
                myScore = uiState.scores.getValue(CARROM_HUMAN),
                theirScore = uiState.scores.getValue(CARROM_COMPUTER),
                theirLabel = "Computer",
                modifier = Modifier.padding(top = 12.dp)
            )

            if (uiState.isGameOver && uiState.winner != CARROM_HUMAN) {
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
                    progress = { secondsLeft / CARROM_TURN_SECONDS.toFloat() },
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
fun ScoreLegend(myColor: CoinColor?, myScore: Int, theirScore: Int, theirLabel: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        val colors = myColor?.let { "You: ${it.name.lowercase()} · " } ?: "Board open - first pot picks your color · "
        Text(
            text = "${colors}You $myScore - $theirScore $theirLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

/**
 * The board. Drag back from the striker to aim; release to shoot. With
 * [flipped] the board is turned 180° so a top-baseline player (online
 * player 2) shoots "up" from the bottom of their screen - aiming still
 * reports directions in board space, since the rotation applies to touch
 * input as well as drawing.
 */
@Composable
fun CarromBoardView(
    coins: List<Coin>,
    canAim: Boolean,
    onShoot: (Vec2, Float) -> Unit,
    modifier: Modifier = Modifier,
    flipped: Boolean = false
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    val scale = if (canvasSize.width > 0) canvasSize.width / CarromBoard.SIZE else 0f
    val striker = coins.firstOrNull { it.id == STRIKER_ID && !it.pocketed }
    val strikerScreenPos = striker?.let { Offset(it.position.x * scale, it.position.y * scale) }

    val maxDragPx = canvasSize.width * 0.4f
    val minDragPx = canvasSize.width * 0.02f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer { rotationZ = if (flipped) 180f else 0f }
            .onSizeChanged { canvasSize = it }
            .pointerInput(canAim, strikerScreenPos) {
                if (!canAim) return@pointerInput
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

        coins.filterNot { it.pocketed }.forEach { coin ->
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
        if (start != null && end != null && canAim) {
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
