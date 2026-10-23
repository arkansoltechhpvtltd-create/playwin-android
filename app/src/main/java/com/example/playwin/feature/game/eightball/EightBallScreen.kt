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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as ImageCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint as ComposePaint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.feature.game.GameScaffold
import com.example.playwin.R
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.domain.eightball.Ball
import com.example.playwin.core.domain.eightball.BallGroup
import com.example.playwin.core.domain.eightball.BallInHand
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
import kotlin.math.ceil
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
        onPlayAgain = viewModel::onPlayAgain,
        onPlaceCueBall = viewModel::onPlaceCueBall
    )
}

/** What the player to shoot is told: how to aim, and with ball in hand, where the cue ball may go first. */
fun ballInHandPrompt(ballInHand: BallInHand?): String = when (ballInHand) {
    BallInHand.ANYWHERE -> "Ball in hand - drag the cue ball anywhere, then drag back to shoot"
    BallInHand.BEHIND_HEAD_STRING -> "Ball in hand - place the cue ball behind the line, then drag back to shoot"
    null -> "Your shot - drag back from the cue ball"
}

/** The banners keep the usual 16dp side margin; only the table runs wider. */
private val BannerInset = Modifier.padding(horizontal = 8.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EightBallScreen(
    uiState: EightBallUiState,
    onBack: () -> Unit,
    onShoot: (direction: Vec2, power: Float) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
    onPlaceCueBall: ((Vec2) -> Unit)? = null
) {
    GameScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "8-Ball · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val foul = if (uiState.endedOnFoul) " on a foul" else ""
            val humanWon = uiState.winner == EIGHT_BALL_HUMAN
            when (uiState.winner) {
                EIGHT_BALL_HUMAN -> StatusBanner(text = "You win$foul! Claiming your coupon…", trophy = true, modifier = BannerInset)
                EIGHT_BALL_COMPUTER -> StatusBanner(text = "The computer wins$foul", modifier = BannerInset)
                else -> TurnBanner(
                    text = when {
                        uiState.isComputerThinking -> "Computer is lining up a shot…"
                        uiState.currentPlayer == EIGHT_BALL_HUMAN -> ballInHandPrompt(uiState.match.ballInHand)
                        else -> "Computer's shot"
                    } + buildString {
                        if (uiState.lastTurnTimedOut) append(" (previous turn timed out)")
                        uiState.lastFoul?.let {
                            append(if (uiState.lastFoulBy == EIGHT_BALL_HUMAN) " (your foul: " else " (computer's foul: ")
                            append("${it.label})")
                        }
                    },
                    secondsLeft = uiState.secondsLeft.takeIf { uiState.currentPlayer == EIGHT_BALL_HUMAN },
                    isBusy = uiState.isBallsMoving || uiState.isResolving || uiState.isComputerThinking,
                    modifier = BannerInset
                )
            }

            BoardSlot(modifier = Modifier.padding(top = 10.dp)) {
                PoolTable(
                    balls = uiState.balls,
                    canAim = uiState.canAim,
                    onShoot = onShoot,
                    ballInHand = uiState.match.ballInHand.takeIf { uiState.canAim },
                    onPlaceCueBall = onPlaceCueBall
                )
                if (humanWon) {
                    CelebrationBurst(
                        colors = listOf(PwGoldDark, PwGoldLight, Color.White, ballColors.getValue(3), ballColors.getValue(2)),
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            GroupLegend(
                myGroup = uiState.playerGroups[EIGHT_BALL_HUMAN],
                modifier = Modifier.padding(top = 10.dp),
                beforeBreak = uiState.match.isBreak
            )

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
            drawBall(id = CUE_BALL_ID, center = center, radius = size.minDimension * 0.42f, label = null)
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
fun GroupLegend(myGroup: BallGroup?, modifier: Modifier = Modifier, beforeBreak: Boolean = false) {
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
                drawBall(id = sample, center = center, radius = size.minDimension * 0.42f, label = null)
            }
        }
        Text(
            text = when {
                myGroup != null -> "You: ${myGroup.name.lowercase()} · then sink the 8"
                // Pots on the break don't assign groups; the first legal pot after it does.
                beforeBreak -> "Table open - groups are set after the break"
                else -> "Table open - next pot picks your group"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = if (myGroup != null) 8.dp else 0.dp)
        )
        if (myGroup != null) {
            Canvas(modifier = Modifier.size(22.dp)) {
                drawBall(id = 8, center = center, radius = size.minDimension * 0.42f, label = null)
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
 * The physics table is 2:1 with six - four corners and one in the middle of each
 * long cushion - so [loadTableImage] reshapes it: cropped to the outside of the
 * rails, keeping the top and bottom middle pockets (they become the side pockets of
 * the long table) and cutting out the band of rows holding the pockets in the middle
 * of the short sides. All in the image's pixels.
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

    // Rows [CUT_TOP, CUT_BOTTOM) are dropped: centred on the short sides' middle
    // pockets (row ~508), and just tall enough to leave the felt exactly 2:1.
    const val CUT_TOP = 313
    const val CUT_BOTTOM = 704

    /**
     * The row cut is cross-faded over this many rows either side of it rather than
     * butted: the artwork is lit from the top, so a hard seam shows across the felt.
     */
    const val FADE = 100

    // The reshaped image, and the felt within it (exactly 2:1, like the physics table).
    const val WIDTH = RIGHT - LEFT
    const val HEIGHT = BOTTOM - TOP - (CUT_BOTTOM - CUT_TOP)
    const val FELT_X = FELT_LEFT - LEFT
    const val FELT_Y = FELT_TOP - TOP
    const val FELT_WIDTH = FELT_RIGHT - FELT_LEFT

    /** Rounds the frame off with the chrome corner caps, as a percentage of the table's height. */
    const val CORNER_PERCENT = 7

    // The rest of the table in the reshaped image, for the light [drawTableDepth]
    // lays over it: the felt's far edges, then each rail's inner edge (the
    // cushions run from there to the felt).
    const val FELT_END = FELT_X + FELT_WIDTH
    const val FELT_BOTTOM = FELT_Y + FELT_WIDTH / 2
    const val RAIL_TOP = 41
    const val RAIL_BOTTOM = 400
    const val RAIL_LEFT = 39
    const val RAIL_RIGHT = 695

    /** The straight runs of rail and cushion between the pockets' chrome: x on the long sides... */
    val LONG_STRAIGHTS = listOf(85f to 336f, 396f to 648f)

    /** ...and y on the short sides. */
    val SHORT_STRAIGHT = 85f to 360f

    /** The middles of the painted pockets' holes: the corners, then the side pockets. */
    val POCKETS = listOf(
        Offset(45.5f, 43.5f), Offset(685.5f, 44f), Offset(45.5f, 395f), Offset(685.5f, 395f),
        Offset(367f, 34f), Offset(366f, 409f)
    )
    const val POCKET_HOLE = 17f
}

/** The reshaped table, built once per process by [loadTableImage]. */
@Volatile
private var tableImageCache: ImageBitmap? = null

/** Builds the reshaped table from eightball_table.jpg (see [TableArt]). Keep this off the main thread. */
private fun loadTableImage(resources: Resources): ImageBitmap = tableImageCache ?: run {
    val source = BitmapFactory.decodeResource(resources, R.drawable.eightball_table)
    val table = Bitmap.createBitmap(TableArt.WIDTH, TableArt.HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(table)
    // Rows [srcTop, srcBottom), across the full width of the rails.
    fun drawRows(srcTop: Int, srcBottom: Int, dstTop: Int) {
        canvas.drawBitmap(
            source,
            AndroidRect(TableArt.LEFT, srcTop, TableArt.RIGHT, srcBottom),
            AndroidRect(0, dstTop, TableArt.WIDTH, dstTop + srcBottom - srcTop),
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

/** How close to the cue ball (in ball radii) a drag must start to pick it up with ball in hand. */
private const val CUE_GRAB_RADII = 2f

/** A ball that has just dropped: it rolls from where it was into the pocket, shrinking away. */
private class SinkingBall(val id: Int, val from: Vec2, val pocket: Vec2) {
    val progress = Animatable(0f)
}

/**
 * The table. Drag back from the cue ball to aim; release to shoot. With
 * [ballInHand], a drag that starts on the cue ball moves it instead, through
 * [onPlaceCueBall] (which keeps it only where the rules allow).
 */
@Composable
fun PoolTable(
    balls: List<Ball>,
    canAim: Boolean,
    onShoot: (Vec2, Float) -> Unit,
    modifier: Modifier = Modifier,
    ballInHand: BallInHand? = null,
    onPlaceCueBall: ((Vec2) -> Unit)? = null
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var movingCue by remember { mutableStateOf(false) }

    val scale = if (canvasSize.width > 0) canvasSize.width / EightBallTable.WIDTH else 0f
    val cueBall = balls.firstOrNull { it.id == CUE_BALL_ID && !it.pocketed }
    val cueScreenPos = cueBall?.let { Offset(it.position.x * scale, it.position.y * scale) }
    // Read through state inside the gesture, so moving the cue ball doesn't restart the gesture mid-drag.
    val currentCuePos = rememberUpdatedState(cueScreenPos)
    val currentPlace = rememberUpdatedState(onPlaceCueBall.takeIf { ballInHand != null })

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
                    val pocket = EightBallTable.pockets.minBy { (it.center - last.position).length }.center
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
            // Sized to fill the white number spot: one digit large, two a little narrower so they still fit.
            fun style(size: Float) = TextStyle(
                color = BallNumberInk,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.04).em,
                fontSize = with(density) { (EightBallTable.BALL_RADIUS * scale * size).toSp() },
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
            (1..15).associateWith { textMeasurer.measure(it.toString(), style(if (it < 10) 0.98f else 0.8f)) }
        }
    }
    val sprites = remember(labels) {
        if (scale <= 0f) null else ballSprites(EightBallTable.BALL_RADIUS * scale, labels, density)
    }
    val tableImage = rememberTableImage()
    val frameShape = RoundedCornerShape(TableArt.CORNER_PERCENT)

    BoxWithConstraints(
        modifier = modifier
            // Stood on end so the long table fills a portrait screen. Everything below,
            // physics coordinates and touch included, still works in the landscape table.
            .quarterTurn()
            .fillMaxWidth()
            .aspectRatio(TableArt.WIDTH / TableArt.HEIGHT.toFloat())
            .drawBehind {
                // A soft drop shadow, lifting the table off the background. It falls towards
                // the bottom of the screen, which is -x here on the turned table.
                val corner = size.minDimension * TableArt.CORNER_PERCENT / 100f
                val step = 2.5.dp.toPx()
                for (i in 1..8) {
                    val grow = i * step
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.05f),
                        topLeft = Offset(-grow - 4 * step, -grow),
                        size = Size(size.width + 2 * grow, size.height + 2 * grow),
                        cornerRadius = CornerRadius(corner + grow)
                    )
                }
            }
            .shadow(elevation = 16.dp, shape = frameShape, ambientColor = Color.Black, spotColor = Color.Black)
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
                drawTableDepth()
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
                .pointerInput(canAim, scale) {
                    if (!canAim) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            val cue = currentCuePos.value
                            val grab = EightBallTable.BALL_RADIUS * scale * CUE_GRAB_RADII
                            movingCue = currentPlace.value != null && cue != null && (offset - cue).getDistance() <= grab
                            if (!movingCue) dragCurrent = offset
                        },
                        onDrag = { change, _ ->
                            if (movingCue) {
                                if (scale > 0f) currentPlace.value?.invoke(Vec2(change.position.x / scale, change.position.y / scale))
                            } else {
                                dragCurrent = change.position
                            }
                        },
                        onDragEnd = {
                            val start = currentCuePos.value
                            val end = dragCurrent
                            if (!movingCue && start != null && end != null) {
                                val pullDistance = (end - start).getDistance()
                                if (pullDistance > minDragPx && maxDragPx > 0f) {
                                    val direction = Vec2(start.x - end.x, start.y - end.y).normalizedOrZero()
                                    val power = (pullDistance / maxDragPx).coerceIn(0f, 1f)
                                    onShoot(direction, power)
                                }
                            }
                            dragCurrent = null
                            movingCue = false
                        },
                        onDragCancel = {
                            dragCurrent = null
                            movingCue = false
                        }
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

                if (ballInHand == BallInHand.BEHIND_HEAD_STRING) {
                    // The kitchen, where the cue ball may go after a foul on the break.
                    drawRect(Color.White.copy(alpha = 0.08f), size = Size(EightBallTable.HEAD_STRING_X * s, size.height))
                }

                if (sprites != null) {
                    // Every shadow first, so none falls across a neighbouring ball.
                    balls.forEach { ball ->
                        if (!ball.pocketed) drawImage(sprites.shadow, toScreen(ball.position) - sprites.offset)
                    }
                    sinking.forEach { sink ->
                        val t = sink.progress.value
                        val center = toScreen(sink.from + (sink.pocket - sink.from) * t)
                        scale(1f - 0.6f * t, pivot = center) {
                            drawImage(sprites.balls.getValue(sink.id), center - sprites.offset, alpha = 1f - t)
                        }
                    }
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
                    sprites?.let { drawImage(it.balls.getValue(ball.id), center - it.offset) }
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
 * artwork: the physics sinks balls at the felt's corners and the middle of each
 * long cushion, in the jaws just in front of the painted pockets.
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
 * The light on the artwork: the felt brighter in the middle and falling off to
 * its edges, with a faint sheen; the rails and cushions rounded off; the pockets
 * deepened. Drawn over it in the reshaped image's pixels (see [TableArt]).
 */
private fun DrawScope.drawTableDepth() = withTransform({ scale(size.width / TableArt.WIDTH, pivot = Offset.Zero) }) {
    val felt = Rect(
        TableArt.FELT_X.toFloat(), TableArt.FELT_Y.toFloat(),
        TableArt.FELT_END.toFloat(), TableArt.FELT_BOTTOM.toFloat()
    )

    // Felt: a vignette, squashed to the felt's 2:1 so all four edges darken alike...
    scale(scaleX = 1f, scaleY = 0.5f, pivot = felt.center) {
        drawRect(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.08f),
                0.45f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.34f),
                center = felt.center,
                radius = felt.width * 0.72f
            ),
            topLeft = Offset(felt.left, felt.center.y - felt.width / 2f),
            size = Size(felt.width, felt.width)
        )
    }
    // ...and the cloth's sheen, from the light at the top-left of the screen (top-right here).
    drawRect(
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.07f),
            1f to Color.Transparent,
            start = felt.topRight,
            end = felt.center
        ),
        topLeft = felt.topLeft,
        size = felt.size
    )

    // Rails, outer edge to inner: falling away at both edges, catching the light on the crown.
    val railLight = arrayOf(
        0f to Color.Black.copy(alpha = 0.22f),
        0.4f to Color.White.copy(alpha = 0.17f),
        0.75f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.2f)
    )
    // Cushions, rail to nose: a highlight along the top of the nose.
    val cushionLight = arrayOf(
        0f to Color.Black.copy(alpha = 0.12f),
        0.65f to Color.Transparent,
        0.86f to Color.White.copy(alpha = 0.2f),
        1f to Color.Transparent
    )
    // The cushions' shadow on the felt, from the nose out.
    val noseShadow = arrayOf(0f to Color.Black.copy(alpha = 0.3f), 1f to Color.Transparent)

    // Lights a band running [from] -> [to] across it, over the run [along] it: in x on the long sides,
    // y on the short. Its ends fade out, so it melts into the pockets' jaws rather than stopping short.
    val fade = 24f
    fun band(stops: Array<Pair<Float, Color>>, from: Float, to: Float, along: Pair<Float, Float>, longSide: Boolean) {
        val (a, b) = along
        val rect = if (longSide) {
            Rect(a, minOf(from, to), b, maxOf(from, to))
        } else {
            Rect(minOf(from, to), a, maxOf(from, to), b)
        }
        val ends = arrayOf(
            0f to Color.Transparent,
            fade / (b - a) to Color.Black,
            1f - fade / (b - a) to Color.Black,
            1f to Color.Transparent
        )
        drawIntoCanvas { it.saveLayer(rect, ComposePaint()) }
        if (longSide) {
            drawRect(Brush.verticalGradient(*stops, startY = from, endY = to), rect.topLeft, rect.size)
            drawRect(Brush.horizontalGradient(*ends, startX = a, endX = b), rect.topLeft, rect.size, blendMode = BlendMode.DstIn)
        } else {
            drawRect(Brush.horizontalGradient(*stops, startX = from, endX = to), rect.topLeft, rect.size)
            drawRect(Brush.verticalGradient(*ends, startY = a, endY = b), rect.topLeft, rect.size, blendMode = BlendMode.DstIn)
        }
        drawIntoCanvas { it.restore() }
    }
    val shade = 10f
    TableArt.LONG_STRAIGHTS.forEach { run ->
        band(railLight, 0f, TableArt.RAIL_TOP.toFloat(), run, longSide = true)
        band(railLight, TableArt.HEIGHT.toFloat(), TableArt.RAIL_BOTTOM.toFloat(), run, longSide = true)
        band(cushionLight, TableArt.RAIL_TOP.toFloat(), felt.top, run, longSide = true)
        band(cushionLight, TableArt.RAIL_BOTTOM.toFloat(), felt.bottom, run, longSide = true)
        band(noseShadow, felt.top, felt.top + shade, run, longSide = true)
        band(noseShadow, felt.bottom, felt.bottom - shade, run, longSide = true)
    }
    TableArt.SHORT_STRAIGHT.let { run ->
        band(railLight, 0f, TableArt.RAIL_LEFT.toFloat(), run, longSide = false)
        band(railLight, TableArt.WIDTH.toFloat(), TableArt.RAIL_RIGHT.toFloat(), run, longSide = false)
        band(cushionLight, TableArt.RAIL_LEFT.toFloat(), felt.left, run, longSide = false)
        band(cushionLight, TableArt.RAIL_RIGHT.toFloat(), felt.right, run, longSide = false)
        band(noseShadow, felt.left, felt.left + shade, run, longSide = false)
        band(noseShadow, felt.right, felt.right - shade, run, longSide = false)
    }

    // Pockets: darkest at the bottom of the hole, with a little light on the far
    // wall (bottom-right on screen; 90-180 degrees here).
    val hole = TableArt.POCKET_HOLE
    TableArt.POCKETS.forEach { pocket ->
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Black,
                0.55f to Color.Black.copy(alpha = 0.85f),
                1f to Color.Transparent,
                center = pocket,
                radius = hole * 1.15f
            ),
            radius = hole * 1.15f,
            center = pocket
        )
        drawArc(
            color = Color.White.copy(alpha = 0.16f),
            startAngle = 100f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = pocket - Offset(hole * 0.85f, hole * 0.85f),
            size = Size(hole * 1.7f, hole * 1.7f),
            style = Stroke(width = 1.4f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Every ball drawn once at the table's size - already turned upright for the
 * turned table - and its shadow, so a physics frame only stamps images: the
 * shading takes gradients, too costly to rebuild for sixteen balls each frame.
 */
private class BallSprites(val balls: Map<Int, ImageBitmap>, val shadow: ImageBitmap) {
    /** From a ball's centre to its sprite's top-left. */
    val offset = Offset(shadow.width / 2f, shadow.height / 2f)
}

private fun ballSprites(radius: Float, labels: Map<Int, TextLayoutResult>, density: Density): BallSprites {
    // Room for the shadow, which reaches past the ball.
    val side = ceil(radius * 3.4f).toInt()
    val size = Size(side.toFloat(), side.toFloat())
    fun sprite(draw: DrawScope.() -> Unit) = ImageBitmap(side, side).also {
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, ImageCanvas(it), size) {
            rotate(degrees = BALL_TURN_BACK, pivot = size.center) { draw() }
        }
    }
    return BallSprites(
        balls = (0..15).associateWith { id ->
            sprite { drawBall(id = id, center = size.center, radius = radius, label = labels[id], shadow = false) }
        },
        shadow = sprite { drawBallShadow(size.center, radius) }
    )
}

/** Where a ball meets the cloth: a soft shadow, cast away from the top-left light. */
private fun DrawScope.drawBallShadow(center: Offset, radius: Float) {
    val c = center + Offset(radius * 0.2f, radius * 0.3f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Black.copy(alpha = 0.6f),
            0.55f to Color.Black.copy(alpha = 0.35f),
            1f to Color.Transparent,
            center = c,
            radius = radius * 1.3f
        ),
        radius = radius * 1.3f,
        center = c
    )
}

/**
 * One ball as a glossy sphere lit from the top-left: its shadow (unless
 * [shadow] is off), the body shaded from a light tint to a dark far edge (for
 * stripes, a white ball with a band of colour round it), the edge falling away,
 * a little light bounced off the cloth, then the number the standard way - a
 * white spot with the number in bold dark ink filling it - and the gloss.
 * [turn] rotates the ball about its centre, so a ball on the turned table still
 * reads upright.
 */
private fun DrawScope.drawBall(
    id: Int,
    center: Offset,
    radius: Float,
    label: TextLayoutResult?,
    shadow: Boolean = true,
    turn: Float = 0f
) = rotate(degrees = turn, pivot = center) {
    if (shadow) drawBallShadow(center, radius)

    val light = center - Offset(radius * 0.38f, radius * 0.42f)
    fun body(base: Color) = Brush.radialGradient(
        0f to lerp(base, Color.White, 0.4f),
        0.4f to base,
        1f to lerp(base, Color.Black, 0.55f),
        center = light,
        radius = radius * 1.7f
    )
    if (ballGroupOf(id) == BallGroup.STRIPES) {
        drawCircle(brush = body(BallIvory), radius = radius, center = center)
        clipPath(Path().apply { addOval(Rect(center = center, radius = radius)) }) {
            drawRect(
                brush = body(colorForBall(id)),
                topLeft = Offset(center.x - radius, center.y - radius * 0.62f),
                size = Size(radius * 2f, radius * 1.24f)
            )
        }
    } else {
        drawCircle(brush = body(colorForBall(id)), radius = radius, center = center)
    }
    // The edge turning away from the light, and the cloth's light bounced back into the bottom-right.
    drawCircle(
        brush = Brush.radialGradient(
            0.72f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.32f),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
    drawArc(
        color = Color.White.copy(alpha = 0.2f),
        startAngle = 15f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = center - Offset(radius * 0.84f, radius * 0.84f),
        size = Size(radius * 1.68f, radius * 1.68f),
        style = Stroke(width = radius * 0.08f, cap = StrokeCap.Round)
    )

    if (id != CUE_BALL_ID) {
        val spot = radius * NUMBER_SPOT
        drawCircle(
            brush = Brush.radialGradient(
                0.6f to Color.White,
                1f to Color(0xFFE4E0D6),
                center = center - Offset(spot * 0.2f, spot * 0.24f),
                radius = spot * 1.2f
            ),
            radius = spot,
            center = center
        )
        drawCircle(Color.Black.copy(alpha = 0.16f), spot, center, style = Stroke(width = radius * 0.04f))
        if (label != null) {
            drawText(textLayoutResult = label, topLeft = center - Offset(label.size.width / 2f, label.size.height / 2f))
        }
    }

    // The gloss: a soft bloom round a sharp catch-light.
    val glint = center - Offset(radius * 0.48f, radius * 0.5f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.8f),
            1f to Color.Transparent,
            center = glint,
            radius = radius * 0.32f
        ),
        radius = radius * 0.32f,
        center = glint
    )
    drawCircle(Color.White.copy(alpha = 0.95f), radius * 0.1f, glint)
}

/** The white number spot: 55% of the ball's diameter. */
private const val NUMBER_SPOT = 0.55f

/** Counters the table's quarter turn, so balls and their numbers stay upright on screen. */
private const val BALL_TURN_BACK = 90f

private val BallNumberInk = Color.Black

/**
 * Lays its content out on its side - measured with width and height swapped -
 * and turns it a quarter anticlockwise, so a landscape table stands upright
 * with its left (head) end at the bottom. Touches are turned with it, so the
 * content still works in its own landscape coordinates.
 */
private fun Modifier.quarterTurn(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(
            minWidth = constraints.minHeight,
            maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth,
            maxHeight = constraints.maxWidth
        )
    )
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(
            x = (placeable.height - placeable.width) / 2,
            y = (placeable.width - placeable.height) / 2
        ) {
            rotationZ = -BALL_TURN_BACK
        }
    }
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
