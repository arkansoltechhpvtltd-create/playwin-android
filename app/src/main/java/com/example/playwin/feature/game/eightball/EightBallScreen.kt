package com.example.playwin.feature.game.eightball

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.domain.eightball.Ball
import com.example.playwin.core.domain.eightball.BallGroup
import com.example.playwin.core.domain.eightball.CUE_BALL_ID
import com.example.playwin.core.domain.eightball.EightBallTable
import com.example.playwin.core.domain.eightball.Vec2
import com.example.playwin.core.domain.eightball.ballGroupOf
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameStatusBanner
import com.example.playwin.feature.game.PhysicsFrameClock
import com.example.playwin.feature.game.TurnTimerRing
import com.example.playwin.feature.game.eightball.online.EightBallOnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
import kotlinx.coroutines.launch

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
            val humanWon = uiState.winner == EIGHT_BALL_HUMAN
            when (uiState.winner) {
                EIGHT_BALL_HUMAN -> StatusBanner(text = "You win$foul! Claiming your coupon…", trophy = true)
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

            Box(modifier = Modifier.padding(top = 16.dp)) {
                PoolTable(
                    balls = uiState.balls,
                    canAim = uiState.canAim,
                    onShoot = onShoot
                )
                if (humanWon) {
                    CelebrationBurst(
                        colors = listOf(PwGoldDark, PwGoldLight, Color.White, ballColors.getValue(3), ballColors.getValue(2)),
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            GroupLegend(myGroup = uiState.playerGroups[EIGHT_BALL_HUMAN], modifier = Modifier.padding(top = 12.dp))

            if (uiState.winner == EIGHT_BALL_COMPUTER) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

@Composable
fun TurnBanner(text: String, secondsLeft: Int?, isBusy: Boolean, modifier: Modifier = Modifier) {
    GameStatusBanner(accent = MaterialTheme.colorScheme.secondary, modifier = modifier) {
        Canvas(modifier = Modifier.size(26.dp)) {
            drawBall(id = CUE_BALL_ID, center = center, radius = size.minDimension * 0.42f, stripeClip = Path(), label = null)
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
            TurnTimerRing(secondsLeft = secondsLeft, totalSeconds = EIGHT_BALL_TURN_SECONDS)
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

/** Which group is yours, shown as a chip with a sample ball of that group and the 8 you finish on. */
@Composable
fun GroupLegend(myGroup: BallGroup?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (myGroup != null) {
            val sample = if (myGroup == BallGroup.SOLIDS) 3 else 10
            Canvas(modifier = Modifier.size(22.dp)) {
                drawBall(id = sample, center = center, radius = size.minDimension * 0.42f, stripeClip = Path(), label = null)
            }
        }
        Text(
            text = when (myGroup) {
                null -> "Table open - first pot picks your group"
                else -> "You: ${myGroup.name.lowercase()} · then sink the 8"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = if (myGroup != null) 8.dp else 0.dp)
        )
        if (myGroup != null) {
            Canvas(modifier = Modifier.size(22.dp)) {
                drawBall(id = 8, center = center, radius = size.minDimension * 0.42f, stripeClip = Path(), label = null)
            }
        }
    }
}

private val FeltLight = Color(0xFF1F7A5C)
private val FeltMid = Color(0xFF14604A)
private val FeltDark = Color(0xFF0B3F30)
private val RailLight = Color(0xFF6E4424)
private val RailDark = Color(0xFF341A0B)
private val BallIvory = Color(0xFFF7F4EA)
private val CueMaple = Color(0xFFE2BE84)
private val CueButt = Color(0xFF3A1D0E)
private val PowerRed = Color(0xFFD5402F)

private val ballColors = mapOf(
    0 to BallIvory,
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

/** The rail thickness around the felt; the pockets are cut into it at the corners. */
private val RAIL = 14.dp

/** A ball that has just dropped: it rolls from where it was into the pocket, shrinking away. */
private class SinkingBall(val id: Int, val from: Vec2, val pocket: Vec2) {
    val progress = Animatable(0f)
}

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

    // Spot balls that were on the table last frame and are pocketed now, and let them sink.
    val sinking = remember { mutableStateListOf<SinkingBall>() }
    val previous = remember { arrayOf(balls) }
    val scope = rememberCoroutineScope()
    SideEffect {
        val before = previous[0]
        if (before !== balls) {
            previous[0] = balls
            if (balls.count { it.pocketed } > before.count { it.pocketed }) {
                balls.filter { it.pocketed }.forEach { ball ->
                    val last = before.firstOrNull { it.id == ball.id && !it.pocketed } ?: return@forEach
                    val pocket = EightBallTable.corners.minBy { (it - last.position).length }
                    val sink = SinkingBall(ball.id, last.position, pocket)
                    sinking += sink
                    scope.launch {
                        sink.progress.animateTo(1f, tween(300, easing = FastOutLinearInEasing))
                        sinking -= sink
                    }
                }
            }
        }
    }

    val cuePulse = if (canAim) {
        rememberInfiniteTransition(label = "poolCueBall").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "poolCueBallPulse"
        )
    } else {
        null
    }

    // Ball numbers are measured once per table size, not once per frame.
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labels = remember(scale, density) {
        if (scale <= 0f) {
            emptyMap()
        } else {
            val style = TextStyle(
                color = Color(0xFF14110C),
                fontWeight = FontWeight.Bold,
                fontSize = with(density) { (EightBallTable.BALL_RADIUS * scale * 0.62f).toSp() }
            )
            (1..15).associateWith { textMeasurer.measure(it.toString(), style) }
        }
    }
    val stripeClip = remember { Path() }
    val frameShape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = frameShape)
            .clip(frameShape)
            .background(Brush.verticalGradient(listOf(RailLight, RailDark, RailLight)))
            .border(1.5.dp, Brush.linearGradient(listOf(PwGoldDark, PwGoldLight, PwGoldDark)), frameShape)
            .padding(RAIL)
    ) {
        Box(
            modifier = Modifier
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
            // The felt, cushions, rail sights and pockets never change, so they
            // keep their own layer and aren't re-recorded while balls roll.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()
            ) {
                drawTableSurface(rail = RAIL.toPx())
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val s = size.width / EightBallTable.WIDTH
                val ballRadius = EightBallTable.BALL_RADIUS * s
                fun toScreen(v: Vec2) = Offset(v.x * s, v.y * s)

                sinking.forEach { sink ->
                    val t = sink.progress.value
                    drawBall(
                        id = sink.id,
                        center = toScreen(sink.from + (sink.pocket - sink.from) * t),
                        radius = ballRadius * (1f - 0.6f * t),
                        stripeClip = stripeClip,
                        label = null,
                        alpha = 1f - t
                    )
                }

                balls.forEach { ball ->
                    if (ball.pocketed) return@forEach
                    val center = toScreen(ball.position)
                    if (ball.id == CUE_BALL_ID && cuePulse != null && dragCurrent == null) {
                        val pulse = cuePulse.value
                        drawCircle(Color.White.copy(alpha = 0.1f + 0.14f * pulse), ballRadius * (1.6f + 0.4f * pulse), center)
                        drawCircle(
                            color = PwGoldDark.copy(alpha = 0.6f + 0.4f * pulse),
                            radius = ballRadius * 1.35f,
                            center = center,
                            style = Stroke(width = ballRadius * 0.14f)
                        )
                    }
                    drawBall(id = ball.id, center = center, radius = ballRadius, stripeClip = stripeClip, label = labels[ball.id])
                }

                val start = cueScreenPos
                val end = dragCurrent
                if (start != null && end != null && canAim) {
                    val pullDistance = (end - start).getDistance()
                    val power = if (maxDragPx > 0f) (pullDistance / maxDragPx).coerceIn(0f, 1f) else 0f
                    val direction = Vec2(start.x - end.x, start.y - end.y).normalizedOrZero()
                    val aim = Offset(direction.x, direction.y)

                    // The shot line: a trail of dots that thins out, longer the harder the pull.
                    val aimLength = (70f + power * 210f) * s
                    val dots = 10
                    for (i in 1..dots) {
                        val f = i / dots.toFloat()
                        drawCircle(
                            color = Color.White.copy(alpha = 0.9f - 0.6f * f),
                            radius = (5f - 2.5f * f) * s,
                            center = start + aim * (ballRadius + aimLength * f)
                        )
                    }

                    // The cue: drawn back from the ball as the pull grows.
                    val tip = start - aim * (ballRadius * 1.4f + power * 70f * s)
                    val joint = tip - aim * (170f * s)
                    val butt = tip - aim * (300f * s)
                    drawLine(Color.Black.copy(alpha = 0.3f), tip + Offset(0f, 5f * s), butt + Offset(0f, 5f * s), 8f * s, StrokeCap.Round)
                    drawLine(CueButt, joint, butt, strokeWidth = 9f * s, cap = StrokeCap.Round)
                    drawLine(PwGoldDark, joint, joint - aim * (8f * s), strokeWidth = 9f * s)
                    drawLine(CueMaple, tip, joint, strokeWidth = 6.5f * s)
                    drawLine(BallIvory, tip, tip - aim * (10f * s), strokeWidth = 6f * s)
                    drawLine(Color(0xFF3F7FBF), tip, tip - aim * (3f * s), strokeWidth = 6f * s)

                    // The power gauge wraps the cue ball and runs from gold to red.
                    val gaugeRadius = ballRadius * 1.9f
                    val gauge = Size(gaugeRadius * 2f, gaugeRadius * 2f)
                    val gaugeTopLeft = start - Offset(gaugeRadius, gaugeRadius)
                    drawArc(
                        color = Color.Black.copy(alpha = 0.3f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = gaugeTopLeft,
                        size = gauge,
                        style = Stroke(width = 5f * s)
                    )
                    drawArc(
                        color = lerp(PwGoldDark, PowerRed, power),
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

/**
 * The felt with its cushions and markings, the gold sights set into the rail
 * (drawn just outside the felt, which this canvas doesn't clip to), and the
 * four corner pockets.
 */
private fun DrawScope.drawTableSurface(rail: Float) {
    val s = size.width / EightBallTable.WIDTH

    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(FeltLight, FeltMid, FeltDark),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.width * 0.62f
        ),
        size = size
    )
    // Cushions: a darker lip all round with a lit edge where it meets the rail.
    drawRect(color = Color.Black.copy(alpha = 0.28f), size = size, style = Stroke(width = 16f * s))
    drawRect(color = Color.White.copy(alpha = 0.12f), size = size, style = Stroke(width = 2f * s))

    // Head string and the two spots.
    val headX = EightBallTable.CUE_START_X * s
    val centerY = EightBallTable.CENTER_Y * s
    val marking = Color.White.copy(alpha = 0.22f)
    drawLine(marking, Offset(headX, 8f * s), Offset(headX, size.height - 8f * s), strokeWidth = 1.5f * s)
    drawCircle(marking, 4f * s, Offset(headX, centerY))
    drawCircle(marking, 4f * s, Offset(EightBallTable.RACK_APEX_X * s, centerY))

    // Rail sights.
    val sight = rail * 0.16f
    for (i in 1..7) {
        if (i == 4) continue
        val x = size.width * i / 8f
        drawCircle(PwGoldDark, sight, Offset(x, -rail / 2f))
        drawCircle(PwGoldDark, sight, Offset(x, size.height + rail / 2f))
    }
    for (i in 1..3) {
        val y = size.height * i / 4f
        drawCircle(PwGoldDark, sight, Offset(-rail / 2f, y))
        drawCircle(PwGoldDark, sight, Offset(size.width + rail / 2f, y))
    }

    val pocketRadius = EightBallTable.POCKET_RADIUS * s
    EightBallTable.corners.forEach { corner ->
        val center = Offset(corner.x * s, corner.y * s)
        drawCircle(PwGoldLight.copy(alpha = 0.7f), pocketRadius * 1.12f, center)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black, Color(0xFF1A130D)),
                center = center,
                radius = pocketRadius
            ),
            radius = pocketRadius,
            center = center
        )
    }
}

/**
 * One ball as a lit sphere: a cast shadow, the colour (a band of it on a white
 * ball for stripes), a darkened edge, the number spot and a catch-light. Flat
 * colours only, as every ball is redrawn each physics frame; [stripeClip] is a
 * scratch path reused between balls for the same reason.
 */
private fun DrawScope.drawBall(
    id: Int,
    center: Offset,
    radius: Float,
    stripeClip: Path,
    label: TextLayoutResult?,
    alpha: Float = 1f
) {
    val color = colorForBall(id).copy(alpha = alpha)
    drawCircle(Color.Black.copy(alpha = 0.35f * alpha), radius, center + Offset(radius * 0.18f, radius * 0.3f))

    if (ballGroupOf(id) == BallGroup.STRIPES) {
        drawCircle(color = BallIvory.copy(alpha = alpha), radius = radius, center = center)
        stripeClip.reset()
        stripeClip.addOval(Rect(center = center, radius = radius))
        clipPath(stripeClip) {
            drawRect(
                color = color,
                topLeft = Offset(center.x - radius, center.y - radius * 0.5f),
                size = Size(radius * 2f, radius)
            )
        }
    } else {
        drawCircle(color = color, radius = radius, center = center)
    }
    // Limb darkening: the edge of a sphere turns away from the light.
    drawCircle(Color.Black.copy(alpha = 0.26f * alpha), radius * 0.91f, center, style = Stroke(width = radius * 0.18f))

    if (id != CUE_BALL_ID) {
        drawCircle(BallIvory.copy(alpha = alpha), radius * 0.46f, center)
        if (label != null) {
            drawText(
                textLayoutResult = label,
                topLeft = center - Offset(label.size.width / 2f, label.size.height / 2f)
            )
        }
    }
    drawCircle(Color.White.copy(alpha = 0.24f * alpha), radius * 0.36f, center - Offset(radius * 0.3f, radius * 0.36f))
    drawCircle(Color.White.copy(alpha = 0.8f * alpha), radius * 0.15f, center - Offset(radius * 0.4f, radius * 0.45f))
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
