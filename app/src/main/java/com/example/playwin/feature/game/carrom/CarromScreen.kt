package com.example.playwin.feature.game.carrom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.domain.carrom.CarromBoard
import com.example.playwin.core.domain.carrom.Coin
import com.example.playwin.core.domain.carrom.CoinColor
import com.example.playwin.core.domain.carrom.QUEEN_ID
import com.example.playwin.core.domain.carrom.STRIKER_ID
import com.example.playwin.core.domain.carrom.Vec2
import com.example.playwin.core.domain.carrom.coinColorOf
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameStatusBanner
import com.example.playwin.feature.game.PhysicsFrameClock
import com.example.playwin.feature.game.TurnTimerRing
import com.example.playwin.feature.game.carrom.online.CarromOnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
import kotlinx.coroutines.launch

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
            val humanWon = uiState.winner == CARROM_HUMAN
            when {
                uiState.isDraw -> StatusBanner(text = "It's a draw! $score")
                humanWon -> StatusBanner(text = "You win $score! Claiming your coupon…", trophy = true)
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

            Box(modifier = Modifier.padding(top = 16.dp)) {
                CarromBoardView(
                    coins = uiState.coins,
                    canAim = uiState.canAim,
                    onShoot = onShoot
                )
                if (humanWon) {
                    CelebrationBurst(
                        colors = listOf(PwGoldDark, PwGoldLight, QueenFace, WhiteFace, MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

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
    GameStatusBanner(accent = MaterialTheme.colorScheme.primary, modifier = modifier) {
        Canvas(modifier = Modifier.size(26.dp)) {
            drawCarromPiece(center = center, radius = size.minDimension * 0.44f, piece = StrikerPiece)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        )
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = if (secondsLeft != null) 10.dp else 0.dp)
                    .size(16.dp),
                strokeWidth = 2.dp
            )
        }
        if (secondsLeft != null) {
            TurnTimerRing(secondsLeft = secondsLeft, totalSeconds = CARROM_TURN_SECONDS)
        }
    }
}

@Composable
private fun StatusBanner(text: String, modifier: Modifier = Modifier, trophy: Boolean = false) {
    val gold = MaterialTheme.colorScheme.primary
    GameStatusBanner(accent = gold, modifier = modifier) {
        if (trophy) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = gold,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(26.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/** The scoreboard under the board: each side's coin colour (once claimed) and coins potted. */
@Composable
fun ScoreLegend(myColor: CoinColor?, myScore: Int, theirScore: Int, theirLabel: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScoreChip(label = "You", score = myScore, color = myColor, modifier = Modifier.weight(1f))
            ScoreChip(label = theirLabel, score = theirScore, color = myColor?.other(), modifier = Modifier.weight(1f))
        }
        if (myColor == null) {
            Text(
                text = "Board open - first pot picks your color",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun ScoreChip(label: String, score: Int, color: CoinColor?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (color != null) {
            Canvas(modifier = Modifier.size(22.dp)) {
                drawCarromPiece(center = center, radius = size.minDimension * 0.44f, piece = pieceFor(color))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (color != null) 8.dp else 0.dp)
        )
        Text(
            text = "$score",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private val WoodLight = Color(0xFFF1D7AE)
private val WoodMid = Color(0xFFE3C090)
private val WoodDark = Color(0xFFCDA36E)
private val FrameLight = Color(0xFF6A4022)
private val FrameDark = Color(0xFF34190B)
private val LineInk = Color(0xFF3E2412)
private val LineRed = Color(0xFFB0342C)
private val QueenFace = Color(0xFFD2304A)
private val WhiteFace = Color(0xFFF4EBD6)

/** The flat colours of one turned wooden piece: its edge, its face and the groove cut into it. */
private class PieceStyle(val rim: Color, val face: Color, val groove: Color)

private val WhitePiece = PieceStyle(rim = Color(0xFFB3A27F), face = WhiteFace, groove = Color(0xFFCBBB9A))
private val BlackPiece = PieceStyle(rim = Color(0xFF0C0C0E), face = Color(0xFF2B2B30), groove = Color(0xFF4C4C55))
private val QueenPiece = PieceStyle(rim = Color(0xFF7E1526), face = QueenFace, groove = Color(0xFFF3B9C2))
private val StrikerPiece = PieceStyle(rim = Color(0xFF9A8C70), face = Color(0xFFFDFBF5), groove = PwGoldLight)

private fun pieceFor(color: CoinColor): PieceStyle = when (color) {
    CoinColor.WHITE -> WhitePiece
    CoinColor.BLACK -> BlackPiece
    CoinColor.QUEEN -> QueenPiece
}

private fun pieceFor(id: Int): PieceStyle = if (id == STRIKER_ID) StrikerPiece else pieceFor(coinColorOf(id))

/** A piece that has just dropped: it slides from where it was into the pocket, shrinking and fading. */
private class SinkingPiece(val id: Int, val from: Vec2, val pocket: Vec2) {
    val progress = Animatable(0f)
}

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

    // Spot pieces that were on the board last frame and are pocketed now, and let them sink.
    val sinking = remember { mutableStateListOf<SinkingPiece>() }
    val previous = remember { arrayOf(coins) }
    val scope = rememberCoroutineScope()
    SideEffect {
        val before = previous[0]
        if (before !== coins) {
            previous[0] = coins
            if (coins.count { it.pocketed } > before.count { it.pocketed }) {
                coins.filter { it.pocketed }.forEach { coin ->
                    val last = before.firstOrNull { it.id == coin.id && !it.pocketed } ?: return@forEach
                    val pocket = CarromBoard.corners.minBy { (it - last.position).length }
                    val piece = SinkingPiece(coin.id, last.position, pocket)
                    sinking += piece
                    scope.launch {
                        piece.progress.animateTo(1f, tween(300, easing = FastOutLinearInEasing))
                        sinking -= piece
                    }
                }
            }
        }
    }

    val strikerPulse = if (canAim) {
        rememberInfiniteTransition(label = "carromStriker").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "carromStrikerPulse"
        )
    } else {
        null
    }
    val gold = MaterialTheme.colorScheme.primary
    val frameShape = RoundedCornerShape(24.dp)
    // Light falls from the top of the *screen*, whichever way the board is turned.
    val lightDir = if (flipped) -1f else 1f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(elevation = 12.dp, shape = frameShape)
            .clip(frameShape)
            .background(Brush.linearGradient(listOf(FrameLight, FrameDark, FrameLight)))
            .border(1.5.dp, Brush.linearGradient(listOf(PwGoldDark, PwGoldLight, PwGoldDark)), frameShape)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
            // The playing surface never changes, so it keeps its own layer and
            // is not re-recorded while the pieces move every frame.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()
            ) {
                drawCarromSurface()
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val s = size.width / CarromBoard.SIZE
                fun toScreen(v: Vec2) = Offset(v.x * s, v.y * s)

                sinking.forEach { piece ->
                    val t = piece.progress.value
                    val at = piece.from + (piece.pocket - piece.from) * t
                    drawCarromPiece(
                        center = toScreen(at),
                        radius = CarromBoard.radiusOf(piece.id) * s * (1f - 0.55f * t),
                        piece = pieceFor(piece.id),
                        lightDir = lightDir,
                        alpha = 1f - t
                    )
                }

                coins.forEach { coin ->
                    if (coin.pocketed) return@forEach
                    val center = toScreen(coin.position)
                    val radius = CarromBoard.radiusOf(coin.id) * s
                    if (coin.id == STRIKER_ID && strikerPulse != null && dragCurrent == null) {
                        val pulse = strikerPulse.value
                        drawCircle(gold.copy(alpha = 0.16f + 0.2f * pulse), radius * (1.5f + 0.3f * pulse), center)
                        drawCircle(
                            color = gold.copy(alpha = 0.6f + 0.4f * pulse),
                            radius = radius * 1.28f,
                            center = center,
                            style = Stroke(width = radius * 0.12f)
                        )
                    }
                    drawCarromPiece(center = center, radius = radius, piece = pieceFor(coin.id), lightDir = lightDir)
                    if (coin.id == QUEEN_ID) {
                        drawCircle(PwGoldDark, radius * 0.6f, center, style = Stroke(width = radius * 0.1f))
                    }
                }

                val start = strikerScreenPos
                val end = dragCurrent
                if (start != null && end != null && canAim) {
                    val pull = end - start
                    val pullDistance = pull.getDistance()
                    val power = if (maxDragPx > 0f) (pullDistance / maxDragPx).coerceIn(0f, 1f) else 0f
                    val direction = Vec2(start.x - end.x, start.y - end.y).normalizedOrZero()
                    val strikerRadius = CarromBoard.STRIKER_RADIUS * s

                    // The pull-back: a taut band from the striker to the finger.
                    drawLine(
                        color = LineInk.copy(alpha = 0.55f),
                        start = start,
                        end = end,
                        strokeWidth = 3f * s,
                        cap = StrokeCap.Round
                    )
                    drawCircle(LineInk.copy(alpha = 0.55f), 6f * s, end)

                    // The shot: a trail of dots that thins out, longer the harder the pull.
                    val aimLength = (60f + power * 190f) * s
                    val dots = 9
                    for (i in 1..dots) {
                        val f = i / dots.toFloat()
                        drawCircle(
                            color = Color.White.copy(alpha = 0.95f - 0.6f * f),
                            radius = (5.5f - 3f * f) * s,
                            center = Offset(
                                start.x + direction.x * (strikerRadius + aimLength * f),
                                start.y + direction.y * (strikerRadius + aimLength * f)
                            )
                        )
                    }

                    // The power gauge wraps the striker and runs from gold to red.
                    val gauge = Size(strikerRadius * 3.2f, strikerRadius * 3.2f)
                    val gaugeTopLeft = start - Offset(strikerRadius * 1.6f, strikerRadius * 1.6f)
                    drawArc(
                        color = Color.Black.copy(alpha = 0.22f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = gaugeTopLeft,
                        size = gauge,
                        style = Stroke(width = 5f * s)
                    )
                    drawArc(
                        color = lerp(PwGoldDark, LineRed, power),
                        startAngle = -90f,
                        sweepAngle = 360f * power,
                        useCenter = false,
                        topLeft = gaugeTopLeft,
                        size = gauge,
                        style = Stroke(width = 5f * s, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

/** The lacquered plywood surface with its printed markings and the four corner pockets. */
private fun DrawScope.drawCarromSurface() {
    val s = size.width / CarromBoard.SIZE
    val boardCenter = Offset(size.width / 2f, size.height / 2f)

    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(WoodLight, WoodMid, WoodDark),
            center = boardCenter,
            radius = size.width * 0.78f
        ),
        size = size
    )
    // A faint inner shadow where the surface meets the raised frame.
    drawRect(color = Color.Black.copy(alpha = 0.18f), size = size, style = Stroke(width = 10f * s))
    drawRect(color = LineInk.copy(alpha = 0.5f), size = size, style = Stroke(width = 3f * s))

    // The same baseline and corner arrow, printed on all four sides.
    val inset = CarromBoard.BASELINE_INSET * s
    val lane = CarromBoard.STRIKER_RADIUS * s
    val left = 190f * s
    val right = size.width - 190f * s
    for (quarter in 0 until 4) {
        rotate(degrees = quarter * 90f, pivot = boardCenter) {
            val y = size.height - inset
            drawLine(LineInk, Offset(left, y - lane), Offset(right, y - lane), strokeWidth = 2.5f * s)
            drawLine(LineInk, Offset(left, y + lane), Offset(right, y + lane), strokeWidth = 1.2f * s)
            listOf(left, right).forEach { x ->
                drawCircle(LineRed, lane, Offset(x, y))
                drawCircle(LineInk, lane, Offset(x, y), style = Stroke(width = 2f * s))
            }

            val arrowStart = Offset(118f * s, size.height - 118f * s)
            val arrowEnd = Offset(250f * s, size.height - 250f * s)
            drawLine(LineInk, arrowStart, arrowEnd, strokeWidth = 1.6f * s, cap = StrokeCap.Round)
            drawArc(
                color = LineInk,
                startAngle = 200f,
                sweepAngle = 230f,
                useCenter = false,
                topLeft = arrowEnd - Offset(22f * s, 22f * s),
                size = Size(44f * s, 44f * s),
                style = Stroke(width = 1.6f * s)
            )
        }
    }

    // Centre: the ring that frames the opening cluster, and the queen's red spot.
    val ring = CarromBoard.COIN_RADIUS * 5.4f * s
    drawCircle(LineInk, ring, boardCenter, style = Stroke(width = 2.5f * s))
    drawCircle(LineInk.copy(alpha = 0.6f), ring + 7f * s, boardCenter, style = Stroke(width = 1.2f * s))
    drawCircle(LineRed.copy(alpha = 0.85f), CarromBoard.COIN_RADIUS * s, boardCenter)

    val pocketRadius = CarromBoard.POCKET_RADIUS * s
    CarromBoard.corners.forEach { corner ->
        val center = Offset(corner.x * s, corner.y * s)
        drawCircle(LineInk.copy(alpha = 0.5f), pocketRadius * 1.14f, center)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black, Color(0xFF17100A)),
                center = center,
                radius = pocketRadius
            ),
            radius = pocketRadius,
            center = center
        )
    }
}

/**
 * One turned wooden piece seen from above: a soft shadow, a darker edge, the
 * face, a cut groove and a catch-light. Drawn with flat colours only, since
 * every piece is redrawn each physics frame. [lightDir] is -1 when the board
 * is rotated 180°, so the shadow still falls toward the bottom of the screen.
 */
private fun DrawScope.drawCarromPiece(
    center: Offset,
    radius: Float,
    piece: PieceStyle,
    lightDir: Float = 1f,
    alpha: Float = 1f
) {
    drawCircle(Color.Black.copy(alpha = 0.3f * alpha), radius * 1.04f, center + Offset(0f, radius * 0.2f * lightDir))
    drawCircle(piece.rim.copy(alpha = alpha), radius, center)
    drawCircle(piece.face.copy(alpha = alpha), radius * 0.88f, center - Offset(0f, radius * 0.04f * lightDir))
    drawCircle(piece.groove.copy(alpha = alpha), radius * 0.56f, center, style = Stroke(width = radius * 0.1f))
    drawCircle(piece.groove.copy(alpha = alpha), radius * 0.2f, center)
    drawArc(
        color = Color.White.copy(alpha = 0.5f * alpha),
        startAngle = if (lightDir > 0f) 200f else 20f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = center - Offset(radius * 0.74f, radius * 0.74f),
        size = Size(radius * 1.48f, radius * 1.48f),
        style = Stroke(width = radius * 0.1f, cap = StrokeCap.Round)
    )
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
