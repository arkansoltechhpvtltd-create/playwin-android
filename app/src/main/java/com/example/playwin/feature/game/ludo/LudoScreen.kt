package com.example.playwin.feature.game.ludo

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.feature.game.GameScaffold
import com.example.playwin.R
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.image.ArtworkCache
import com.example.playwin.core.designsystem.image.artworkPainter
import com.example.playwin.core.designsystem.image.rememberArtwork
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.domain.ludo.LudoColor
import com.example.playwin.core.domain.ludo.LudoEngine
import com.example.playwin.core.domain.ludo.LudoState
import com.example.playwin.core.domain.ludo.Token
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameStatusBanner
import com.example.playwin.feature.game.TurnTimerRing
import com.example.playwin.feature.game.ludo.online.LudoOnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun LudoRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<PlayMode?>(null) }
    // Board and tokens are ready in the background by the time a mode is picked.
    val resources = LocalContext.current.resources
    LaunchedEffect(Unit) { preloadLudoArtwork(resources) }
    when (mode) {
        null -> GameModeSelectScreen(title = "Ludo", onBack = onBack, onSelectMode = { mode = it })
        PlayMode.COMPUTER -> LudoComputerRoute(onBack = { mode = null }, onWin = onWin)
        PlayMode.ONLINE -> LudoOnlineRoute(
            onBack = { mode = null },
            onWin = onWin,
            onPlayComputer = { mode = PlayMode.COMPUTER }
        )
    }
}

@Composable
private fun LudoComputerRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: LudoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    LudoScreen(
        uiState = uiState,
        onBack = onBack,
        onRollDice = viewModel::onRollDice,
        onTokenTap = viewModel::onTokenTap,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LudoScreen(
    uiState: LudoUiState,
    onBack: () -> Unit,
    onRollDice: () -> Unit,
    onTokenTap: (Token) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    GameScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            PwTopBar(title = "Ludo · vs Computer", onBack = onBack) {
                Image(
                    painter = artworkPainter(R.drawable.ludo_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(36.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.winner != null) {
                WinnerBanner(humanWon = uiState.winner == LUDO_HUMAN)
            } else {
                TurnBanner(uiState)
            }

            BoardSlot(modifier = Modifier.padding(top = 16.dp)) {
                LudoBoard(
                    game = uiState.game,
                    movableTokens = uiState.movableTokens,
                    onTokenTap = onTokenTap,
                    celebrate = uiState.winner == LUDO_HUMAN
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LudoDice(
                    value = uiState.game.diceValue,
                    live = uiState.game.diceValue != null,
                    enabled = uiState.canRoll,
                    onClick = onRollDice
                )
                Spacer(modifier = Modifier.width(16.dp))
                PrimaryButton(
                    text = uiState.game.diceValue?.let { "Rolled: $it" } ?: "Roll dice",
                    onClick = onRollDice,
                    enabled = uiState.canRoll
                )
                if (uiState.isResolving) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp).size(18.dp), strokeWidth = 2.dp)
                }
            }

            if (uiState.winner == LUDO_COMPUTER) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

internal fun LudoColor.label(): String = when (this) {
    LudoColor.RED -> "Red"
    LudoColor.GREEN -> "Green"
    LudoColor.YELLOW -> "Yellow"
    LudoColor.BLUE -> "Blue"
}

internal fun colorFor(color: LudoColor): Color = when (color) {
    LudoColor.RED -> Color(0xFFE53935)
    LudoColor.GREEN -> Color(0xFF43A047)
    LudoColor.YELLOW -> Color(0xFFFDD835)
    LudoColor.BLUE -> Color(0xFF1E88E5)
}

@Composable
private fun TurnBanner(uiState: LudoUiState, modifier: Modifier = Modifier) {
    val accent = colorFor(uiState.currentColor)
    val tokenImage = rememberTokenImages()?.getValue(uiState.currentColor)
    GameStatusBanner(accent = accent, modifier = modifier) {
        Canvas(modifier = Modifier.size(26.dp)) {
            if (tokenImage != null) drawToken(center = center, radius = size.minDimension * 0.42f, image = tokenImage)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = if (uiState.isHumanTurn) "Your turn (Red)" else "Computer's turn (Yellow)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (uiState.lastTurnTimedOut) {
                Text(
                    text = "Previous turn timed out",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TurnTimerRing(secondsLeft = uiState.secondsLeft, totalSeconds = LUDO_TURN_SECONDS)
    }
}

@Composable
private fun WinnerBanner(humanWon: Boolean, modifier: Modifier = Modifier) {
    val gold = MaterialTheme.colorScheme.primary
    val computerToken = rememberTokenImages()?.getValue(LUDO_COMPUTER)
    GameStatusBanner(accent = if (humanWon) gold else colorFor(LUDO_COMPUTER), modifier = modifier) {
        if (humanWon) {
            Icon(imageVector = Icons.Filled.EmojiEvents, contentDescription = null, tint = gold, modifier = Modifier.size(26.dp))
        } else {
            Canvas(modifier = Modifier.size(26.dp)) {
                if (computerToken != null) drawToken(center = center, radius = size.minDimension * 0.42f, image = computerToken)
            }
        }
        Text(
            text = if (humanWon) "You win! Claiming your coupon…" else "The computer wins this round",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )
    }
}

// --- Board geometry: ludo_board.png is a classic 15x15 board - 6x6 yards in the
// corners (Red top-left, Green top-right, Yellow bottom-right, Blue bottom-left), a
// cross-shaped track 3 cells wide, each colour's home column down the middle lane of
// its arm, and the centre. Cells are (row, col), both 0..14 from the top-left, and
// cell (row, col) is centred at grid point (col + 0.5, row + 0.5). ---

private const val GRID = 15

/**
 * The 52 shared-track cells in path order, clockwise, as painted on the board:
 * index 0 is Red's start square (row 6, col 1), and each colour's start
 * ([LudoEngine.startIndexFor]) is 13 cells on - Green (1,8), Yellow (8,13),
 * Blue (13,6). The cell 50 on from a start, where that colour turns off, is
 * the end of its home lane: Red (7,0), Green (0,7), Yellow (7,14), Blue (14,7).
 */
private fun ringCells(): List<Pair<Int, Int>> {
    val cells = mutableListOf<Pair<Int, Int>>()
    for (c in 1..5) cells += 6 to c // Red's arm, top lane, heading right
    for (r in 5 downTo 0) cells += r to 6 // up the top arm's left lane
    cells += 0 to 7
    for (r in 0..5) cells += r to 8 // down the top arm's right lane
    for (c in 9..14) cells += 6 to c // along the right arm's top lane
    cells += 7 to 14
    for (c in 14 downTo 9) cells += 8 to c // back along its bottom lane
    for (r in 9..14) cells += r to 8 // down the bottom arm's right lane
    cells += 14 to 7
    for (r in 14 downTo 9) cells += r to 6 // up its left lane
    for (c in 5 downTo 0) cells += 8 to c // along the left arm's bottom lane
    cells += 7 to 0
    cells += 6 to 0
    return cells
}

internal val RING_CELLS = ringCells()

/** The centre of the board, where every colour's tokens finish. */
private val HOME_CELL = 7 to 7

/** Each colour's six home steps: five down the middle lane of its arm, then the centre. */
internal fun homeColumnCells(color: LudoColor): List<Pair<Int, Int>> = when (color) {
    LudoColor.RED -> (1..5).map { 7 to it }
    LudoColor.GREEN -> (1..5).map { it to 7 }
    LudoColor.YELLOW -> (13 downTo 9).map { 7 to it }
    LudoColor.BLUE -> (13 downTo 9).map { it to 7 }
} + HOME_CELL

/**
 * The yard slots: each yard's four painted rings, which aren't on grid-cell
 * centres. Base tokens are given a stand-in cell in their yard (never a track
 * cell) that [gridPoint] maps onto the ring, in grid units. Measured from
 * ludo_board.png (rings at 140/291 and 788/939 px of 1080).
 */
private val YARD_NEAR = listOf(1.943f, 4.043f)
private val YARD_FAR = listOf(10.943f, 13.043f)

private fun yardSlots(color: LudoColor): List<Offset> {
    val xs = if (color == LudoColor.GREEN || color == LudoColor.YELLOW) YARD_FAR else YARD_NEAR
    val ys = if (color == LudoColor.YELLOW || color == LudoColor.BLUE) YARD_FAR else YARD_NEAR
    return ys.flatMap { y -> xs.map { x -> Offset(x, y) } }
}

/** Stand-in yard cells for base tokens, one per token index, mapped to [yardSlots] by [gridPoint]. */
private fun yardCell(color: LudoColor, index: Int): Pair<Int, Int> {
    val top = if (color == LudoColor.YELLOW || color == LudoColor.BLUE) 9 else 0
    val left = if (color == LudoColor.GREEN || color == LudoColor.YELLOW) 9 else 0
    return (top + 1 + index / 2 * 3) to (left + 1 + index % 2 * 3)
}

private val YARD_POINTS: Map<Pair<Int, Int>, Offset> = LudoEngine.PLAYER_ORDER.flatMap { color ->
    yardSlots(color).mapIndexed { index, point -> yardCell(color, index) to point }
}.toMap()

private fun cellFor(color: LudoColor, index: Int, position: Int): Pair<Int, Int> = when {
    position == LudoEngine.IN_BASE -> yardCell(color, index)
    position < LudoEngine.PLAYER_PATH_LENGTH -> RING_CELLS[LudoEngine.globalIndexOf(color, position)]
    else -> homeColumnCells(color)[position - LudoEngine.PLAYER_PATH_LENGTH]
}

private fun cellFor(token: Token): Pair<Int, Int> = cellFor(token.color, token.index, token.position)

/** Where a cell is drawn, in grid units (multiply by the cell size for pixels): its centre, or a yard ring. */
internal fun gridPoint(cell: Pair<Int, Int>): Offset {
    YARD_POINTS[cell]?.let { return it }
    val (row, col) = cell
    return Offset(col + 0.5f, row + 0.5f)
}

private const val HOP_MILLIS_PER_CELL = 110
private const val FLIGHT_MILLIS = 420
private const val HOP_ARC = 0.45f
private const val FLIGHT_ARC = 1.3f

/**
 * Where one token is drawn right now. A forward move hops cell by cell along
 * the track; leaving base, or being captured and sent back, is one long arc.
 * Everything is in grid units and read only in the draw phase, so an
 * animating token redraws the token layer without recomposing anything.
 */
private class TokenMotion(token: Token) {
    private var position = token.position
    private var waypoints by mutableStateOf(listOf(gridPoint(cellFor(token))))
    private var arc = HOP_ARC
    private val progress = Animatable(0f)

    /** Nudges tokens sharing a cell apart so each stays visible. */
    val spread = Animatable(Offset.Zero, Offset.VectorConverter)

    private fun segment(): Pair<Int, Float> {
        val p = progress.value.coerceIn(0f, (waypoints.size - 1).toFloat())
        val i = p.toInt().coerceAtMost(waypoints.size - 2)
        return i to (p - i)
    }

    fun offset(): Offset {
        if (waypoints.size == 1) return waypoints[0]
        val (i, fraction) = segment()
        return lerp(waypoints[i], waypoints[i + 1], fraction)
    }

    /** How far off the board the token is mid-hop, in grid units. */
    fun lift(): Float {
        if (waypoints.size == 1) return 0f
        return sin(segment().second * PI.toFloat()) * arc
    }

    suspend fun moveTo(token: Token) {
        val from = position
        val to = token.position
        if (from == to) return
        val here = offset()
        position = to
        val hops = from != LudoEngine.IN_BASE && to > from
        val points = if (hops) {
            listOf(here) + (from + 1..to).map { gridPoint(cellFor(token.color, token.index, it)) }
        } else {
            listOf(here, gridPoint(cellFor(token)))
        }
        progress.snapTo(0f)
        arc = if (hops) HOP_ARC else FLIGHT_ARC
        waypoints = points
        val segments = points.size - 1
        progress.animateTo(
            targetValue = segments.toFloat(),
            animationSpec = if (hops) {
                tween(HOP_MILLIS_PER_CELL * segments, easing = LinearEasing)
            } else {
                tween(FLIGHT_MILLIS, easing = FastOutSlowInEasing)
            }
        )
    }
}

/** How much of ludo_token.png's width the disc itself covers. */
private const val TOKEN_DISC_FRACTION = 0.724f
private const val TOKEN_BITMAP_PX = 192

/** The tinted tokens, built once per process by [loadTokenImages]. */
@Volatile
private var tokenImageCache: Map<LudoColor, ImageBitmap>? = null

/**
 * ludo_token.png in each player's colour. Only the red body is recoloured (hue
 * swapped, saturation/brightness matched to the target), so the gold rim and the
 * white highlights stay as painted. A per-pixel pass, so keep it off the main thread.
 */
private fun loadTokenImages(resources: Resources): Map<LudoColor, ImageBitmap> = tokenImageCache ?: run {
    val source = BitmapFactory.decodeResource(resources, R.drawable.ludo_token)
    val scaled = Bitmap.createScaledBitmap(source, TOKEN_BITMAP_PX, TOKEN_BITMAP_PX, true)
    LudoEngine.PLAYER_ORDER.associateWith { tintToken(scaled, it).asImageBitmap() }
        .also { tokenImageCache = it }
}

/** Decodes the board, header badge and tinted tokens ahead of the board appearing. */
private suspend fun preloadLudoArtwork(resources: Resources) = withContext(Dispatchers.Default) {
    ArtworkCache.load(resources, R.drawable.ludo_board)
    ArtworkCache.load(resources, R.drawable.ludo_logo)
    loadTokenImages(resources)
}

/** The tinted tokens; null for the frame or two before they're ready if they weren't preloaded. */
@Composable
private fun rememberTokenImages(): Map<LudoColor, ImageBitmap>? {
    val resources = LocalContext.current.resources
    val images by produceState(initialValue = tokenImageCache) {
        if (value == null) value = withContext(Dispatchers.Default) { loadTokenImages(resources) }
    }
    return images
}

private fun tintToken(source: Bitmap, color: LudoColor): Bitmap {
    if (color == LudoColor.RED) return source
    val from = FloatArray(3).also { android.graphics.Color.colorToHSV(colorFor(LudoColor.RED).toArgb(), it) }
    val to = FloatArray(3).also { android.graphics.Color.colorToHSV(colorFor(color).toArgb(), it) }
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    val hsv = FloatArray(3)
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val alpha = pixel ushr 24
        if (alpha == 0) continue
        android.graphics.Color.colorToHSV(pixel, hsv)
        // Distance from red on the colour wheel: the body is near 0°, the gold rim near 40°.
        val fromRed = min(hsv[0], 360f - hsv[0])
        val weight = ((26f - fromRed) / 8f).coerceIn(0f, 1f)
        if (weight == 0f) continue
        hsv[0] = to[0]
        hsv[1] = (hsv[1] * to[1] / from[1]).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * to[2] / from[2]).coerceIn(0f, 1f)
        val tinted = android.graphics.Color.HSVToColor(alpha, hsv)
        pixels[i] = if (weight == 1f) tinted else ColorUtils.blendARGB(pixel, tinted, weight)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private typealias TokenKey = Pair<LudoColor, Int>

@Composable
private fun rememberMovablePulse(): State<Float> {
    val transition = rememberInfiniteTransition(label = "ludoMovable")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ludoMovablePulse"
    )
}

@Composable
fun LudoBoard(
    game: LudoState,
    movableTokens: List<Token>,
    onTokenTap: (Token) -> Unit,
    modifier: Modifier = Modifier,
    celebrate: Boolean = false
) {
    val boardImage = rememberArtwork(R.drawable.ludo_board)
    val tokenImages = rememberTokenImages()
    val gold = MaterialTheme.colorScheme.primary
    val frameShape = RoundedCornerShape(22.dp)
    val boardShape = RoundedCornerShape(18.dp)

    val motions = remember { HashMap<TokenKey, TokenMotion>() }
    // Tokens resting on the same track cell fan out around its centre.
    val spreads = remember(game.tokens) {
        game.tokens
            .filter { it.position != LudoEngine.IN_BASE }
            .groupBy(::cellFor)
            .values
            .filter { it.size > 1 }
            .flatMap { group ->
                group.mapIndexed { i, token ->
                    val angle = 2f * PI.toFloat() * i / group.size - PI.toFloat() / 2f
                    (token.color to token.index) to Offset(cos(angle), sin(angle)) * 0.22f
                }
            }
            .toMap()
    }
    for (token in game.tokens) {
        key(token.color, token.index) {
            val motion = motions.getOrPut(token.color to token.index) { TokenMotion(token) }
            LaunchedEffect(token.position) { motion.moveTo(token) }
            val spread = spreads[token.color to token.index] ?: Offset.Zero
            LaunchedEffect(spread) { motion.spread.animateTo(spread, tween(200)) }
        }
    }
    val pulse = if (movableTokens.isNotEmpty()) rememberMovablePulse() else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(elevation = 12.dp, shape = frameShape)
            .clip(frameShape)
            .background(Brush.linearGradient(listOf(PwGoldDark, PwGoldLight, Color(0xFF7A5A14), PwGoldLight)))
            .padding(4.dp)
            .clip(boardShape)
    ) {
        // The static board sits in its own layer so animating tokens never re-record it.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
        ) {
            if (boardImage != null) drawBoard(image = boardImage)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(movableTokens) {
                    detectTapGestures { offset ->
                        val cell = size.width / GRID.toFloat()
                        val hit = movableTokens.minByOrNull { token ->
                            (gridPoint(cellFor(token)) * cell - offset).getDistance()
                        }
                        if (hit != null && (gridPoint(cellFor(hit)) * cell - offset).getDistance() < cell) {
                            onTokenTap(hit)
                        }
                    }
                }
        ) {
            val images = tokenImages ?: return@Canvas
            val cell = size.width / GRID
            val glow = pulse?.value
            game.tokens
                .mapNotNull { token -> motions[token.color to token.index]?.let { token to it } }
                .sortedBy { (_, motion) -> motion.lift() }
                .forEach { (token, motion) ->
                    val key = token.color to token.index
                    val isMovable = movableTokens.any { it.color == token.color && it.index == token.index }
                    val stacked = key in spreads
                    drawToken(
                        center = (motion.offset() + motion.spread.value) * cell,
                        radius = cell * (if (stacked) 0.32f else 0.4f),
                        image = images.getValue(token.color),
                        lift = motion.lift() * cell,
                        glow = if (isMovable) glow else null,
                        glowColor = gold
                    )
                }
        }

        if (celebrate) {
            CelebrationBurst(
                colors = listOf(PwGoldDark, PwGoldLight, colorFor(LUDO_HUMAN), MaterialTheme.colorScheme.secondary, Color.White),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** The board is ludo_board.png, as painted: its track, yards and home columns are the ones the tokens use. */
private fun DrawScope.drawBoard(image: ImageBitmap) {
    drawImage(
        image = image,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.High
    )
}

/**
 * A playing piece drawn from [image]. [center] is where it stands on the board;
 * [lift] raises the piece off its shadow mid-hop. A non-null [glow] (0..1)
 * marks it as tappable with a breathing halo in [glowColor].
 */
private fun DrawScope.drawToken(
    center: Offset,
    radius: Float,
    image: ImageBitmap,
    lift: Float = 0f,
    glow: Float? = null,
    glowColor: Color = Color.Transparent
) {
    val airborne = (lift / (radius * 3f)).coerceIn(0f, 1f)
    val r = radius * (1f + (glow ?: 0f) * 0.08f + airborne * 0.15f)
    val body = center - Offset(0f, lift)

    drawCircle(
        color = Color.Black.copy(alpha = 0.32f * (1f - airborne * 0.5f)),
        radius = r * (1f - airborne * 0.2f),
        center = center + Offset(0f, r * 0.22f)
    )
    if (glow != null) {
        drawCircle(color = glowColor.copy(alpha = 0.18f + 0.22f * glow), radius = r * (1.35f + 0.25f * glow), center = body)
        drawCircle(
            color = glowColor.copy(alpha = 0.7f + 0.3f * glow),
            radius = r * 1.2f,
            center = body,
            style = Stroke(width = r * 0.14f)
        )
    }
    val side = (2f * r / TOKEN_DISC_FRACTION).roundToInt()
    drawImage(
        image = image,
        dstOffset = IntOffset((body.x - side / 2f).roundToInt(), (body.y - side / 2f).roundToInt()),
        dstSize = IntSize(side, side),
        filterQuality = FilterQuality.High
    )
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun LudoScreenPreview() {
    PlayWinTheme {
        LudoScreen(
            uiState = LudoUiState(),
            onBack = {},
            onRollDice = {},
            onTokenTap = {},
            onPlayAgain = {}
        )
    }
}
