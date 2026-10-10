package com.example.playwin.feature.game.eightball

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Rect as AndroidRect
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
import com.example.playwin.R
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
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameStatusBanner
import com.example.playwin.feature.game.PhysicsFrameClock
import com.example.playwin.feature.game.TurnTimerRing
import com.example.playwin.feature.game.eightball.online.EightBallOnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Entry point for 8-Ball: play the computer, or get paired with a random online player. */
@Composable
fun EightBallRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<PlayMode?>(null) }
    // Builds the table artwork while the mode is being picked.
    val resources = LocalContext.current.resources
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { loadTableImage(resources) } }
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

            BoardSlot(modifier = Modifier.padding(top = 16.dp)) {
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

/**
 * Where things are in eightball_table.jpg, a near-square table with eight pockets.
 * The physics table is 2:1 with only the four corner pockets, so [loadTableImage]
 * reshapes it: cropped to the outside of the rails, with the band of columns holding
 * the top and bottom middle pockets cut out, and the band of rows holding the two
 * side pockets cut out. All in the image's pixels.
 */
private object TableArt {
    // The outside of the rails.
    const val LEFT = 148
    const val TOP = 94
    const val RIGHT = 882
    const val BOTTOM = 930

    // The cushion noses, which the balls bounce off: the edges of the physics table.
    const val FELT_LEFT = 216
    const val FELT_TOP = 168
    const val FELT_RIGHT = 812

    // Columns [CUT_LEFT, CUT_RIGHT) and rows [CUT_TOP, CUT_BOTTOM) are dropped.
    const val CUT_LEFT = 482
    const val CUT_RIGHT = 548
    const val CUT_TOP = 300
    const val CUT_BOTTOM = 724

    /**
     * The row cut is cross-faded over this many rows either side of it rather than
     * butted: the artwork is lit from the top, so a hard seam shows across the felt.
     */
    const val FADE = 100

    // The reshaped image, and the felt within it (exactly 2:1, like the physics table).
    const val WIDTH = RIGHT - LEFT - (CUT_RIGHT - CUT_LEFT)
    const val HEIGHT = BOTTOM - TOP - (CUT_BOTTOM - CUT_TOP)
    const val FELT_X = FELT_LEFT - LEFT
    const val FELT_Y = FELT_TOP - TOP
    const val FELT_WIDTH = FELT_RIGHT - FELT_LEFT - (CUT_RIGHT - CUT_LEFT)

    /** Rounds the frame off with the chrome corner caps, as a percentage of the table's height. */
    const val CORNER_PERCENT = 7
}

/** The reshaped table, built once per process by [loadTableImage]. */
@Volatile
private var tableImageCache: ImageBitmap? = null

/** Builds the reshaped table from eightball_table.jpg (see [TableArt]). Keep this off the main thread. */
private fun loadTableImage(resources: Resources): ImageBitmap = tableImageCache ?: run {
    val source = BitmapFactory.decodeResource(resources, R.drawable.eightball_table)
    val table = Bitmap.createBitmap(TableArt.WIDTH, TableArt.HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(table)
    // Rows [srcTop, srcBottom) of both column halves, either side of the column cut.
    fun drawRows(srcTop: Int, srcBottom: Int, dstTop: Int) {
        val dstBottom = dstTop + srcBottom - srcTop
        val leftWidth = TableArt.CUT_LEFT - TableArt.LEFT
        canvas.drawBitmap(
            source,
            AndroidRect(TableArt.LEFT, srcTop, TableArt.CUT_LEFT, srcBottom),
            AndroidRect(0, dstTop, leftWidth, dstBottom),
            null
        )
        canvas.drawBitmap(
            source,
            AndroidRect(TableArt.CUT_RIGHT, srcTop, TableArt.RIGHT, srcBottom),
            AndroidRect(leftWidth, dstTop, TableArt.WIDTH, dstBottom),
            null
        )
    }
    val fadeTop = TableArt.CUT_TOP - TableArt.FADE - TableArt.TOP
    val fadeBottom = TableArt.CUT_TOP + TableArt.FADE - TableArt.TOP
    drawRows(TableArt.TOP, TableArt.CUT_TOP + TableArt.FADE, dstTop = 0)
    // The lower half goes on top, faded in across the band either side of the cut.
    val layer = canvas.saveLayer(null, null)
    drawRows(TableArt.CUT_BOTTOM - TableArt.FADE, TableArt.BOTTOM, dstTop = fadeTop)
    canvas.drawRect(
        0f,
        fadeTop.toFloat(),
        TableArt.WIDTH.toFloat(),
        fadeBottom.toFloat(),
        Paint().apply {
            shader = LinearGradient(
                0f, fadeTop.toFloat(), 0f, fadeBottom.toFloat(),
                android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK, Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
    )
    canvas.restoreToCount(layer)
    source.recycle()
    table.asImageBitmap().also { tableImageCache = it }
}

/** The reshaped table; null for the frame or two before it's ready if it wasn't preloaded. */
@Composable
private fun rememberTableImage(): ImageBitmap? {
    val resources = LocalContext.current.resources
    val image by produceState(initialValue = tableImageCache) {
        if (value == null) value = withContext(Dispatchers.Default) { loadTableImage(resources) }
    }
    return image
}

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
    val tableImage = rememberTableImage()
    val frameShape = RoundedCornerShape(TableArt.CORNER_PERCENT)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(TableArt.WIDTH / TableArt.HEIGHT.toFloat())
            .shadow(elevation = 12.dp, shape = frameShape)
            .clip(frameShape)
            .background(RailDark)
    ) {
        if (tableImage != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawImage(
                    image = tableImage,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.High
                )
            }
        }
        // The play area sits on the artwork's felt, between the cushion noses.
        val artPixel = maxWidth / TableArt.WIDTH
        Box(
            modifier = Modifier
                .padding(start = artPixel * TableArt.FELT_X, top = artPixel * TableArt.FELT_Y)
                .width(artPixel * TableArt.FELT_WIDTH)
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
            // The markings never change, so they keep their own layer and
            // aren't re-recorded while balls roll.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()
            ) {
                drawTableMarkings()
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
 * The head string and spots on the artwork's felt. The pockets come from the
 * artwork: the physics sinks balls at the felt's corners, in the jaws just in
 * front of the painted pockets.
 */
private fun DrawScope.drawTableMarkings() {
    val s = size.width / EightBallTable.WIDTH

    // Head string and the two spots.
    val headX = EightBallTable.CUE_START_X * s
    val centerY = EightBallTable.CENTER_Y * s
    val marking = Color.White.copy(alpha = 0.22f)
    drawLine(marking, Offset(headX, 8f * s), Offset(headX, size.height - 8f * s), strokeWidth = 1.5f * s)
    drawCircle(marking, 4f * s, Offset(headX, centerY))
    drawCircle(marking, 4f * s, Offset(EightBallTable.RACK_APEX_X * s, centerY))
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
