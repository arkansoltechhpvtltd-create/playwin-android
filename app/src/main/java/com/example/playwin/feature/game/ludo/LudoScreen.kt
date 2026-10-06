package com.example.playwin.feature.game.ludo

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.R
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.domain.ludo.LudoColor
import com.example.playwin.core.domain.ludo.LudoEngine
import com.example.playwin.core.domain.ludo.LudoState
import com.example.playwin.core.domain.ludo.Token
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameStatusBanner
import com.example.playwin.feature.game.TurnTimerRing
import com.example.playwin.feature.game.ludo.online.LudoOnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
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
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            PwTopBar(title = "Ludo · vs Computer", onBack = onBack) {
                Image(
                    painter = painterResource(R.drawable.ludo_logo),
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

            LudoBoard(
                game = uiState.game,
                movableTokens = uiState.movableTokens,
                onTokenTap = onTokenTap,
                modifier = Modifier.padding(top = 16.dp),
                celebrate = uiState.winner == LUDO_HUMAN
            )

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
    val tokenImage = rememberTokenImages().getValue(uiState.currentColor)
    GameStatusBanner(accent = accent, modifier = modifier) {
        Canvas(modifier = Modifier.size(26.dp)) {
            drawToken(center = center, radius = size.minDimension * 0.42f, image = tokenImage)
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
    val computerToken = rememberTokenImages().getValue(LUDO_COMPUTER)
    GameStatusBanner(accent = if (humanWon) gold else colorFor(LUDO_COMPUTER), modifier = modifier) {
        if (humanWon) {
            Icon(imageVector = Icons.Filled.EmojiEvents, contentDescription = null, tint = gold, modifier = Modifier.size(26.dp))
        } else {
            Canvas(modifier = Modifier.size(26.dp)) {
                drawToken(center = center, radius = size.minDimension * 0.42f, image = computerToken)
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

// --- Board geometry: a 15x15 grid with a 52-cell ring track, 4 private home-column
// stripes converging near the centre, and 4 corner base areas. This is a clean,
// internally-consistent layout rather than a pixel replica of a physical board.
// The playable cells are rows/columns 1..14, so everything is drawn shifted by
// half a cell to sit centred: cell (row, col) is centred at grid point (col, row). ---

private const val GRID = 15

private fun ringCells(): List<Pair<Int, Int>> {
    val cells = mutableListOf<Pair<Int, Int>>()
    for (c in 1..14) cells += 1 to c
    for (r in 2..14) cells += r to 14
    for (c in 13 downTo 1) cells += 14 to c
    for (r in 13 downTo 2) cells += r to 1
    return cells
}

private val RING_CELLS = ringCells()

private fun homeColumnCells(color: LudoColor): List<Pair<Int, Int>> = when (color) {
    LudoColor.RED -> (2..7).map { 7 to it }
    LudoColor.GREEN -> (2..7).map { it to 7 }
    LudoColor.YELLOW -> (12 downTo 7).map { 7 to it }
    LudoColor.BLUE -> (12 downTo 7).map { it to 7 }
}

private fun baseTopLeft(color: LudoColor): Pair<Int, Int> = when (color) {
    LudoColor.RED -> 2 to 2
    LudoColor.GREEN -> 2 to 9
    LudoColor.YELLOW -> 9 to 9
    LudoColor.BLUE -> 9 to 2
}

private val BASE_SLOT_OFFSETS = listOf(1 to 1, 1 to 3, 3 to 1, 3 to 3)

private val HOME_CELL = 7 to 7

private fun cellFor(color: LudoColor, index: Int, position: Int): Pair<Int, Int> = when {
    position == LudoEngine.IN_BASE -> {
        val (tr, tc) = baseTopLeft(color)
        val (or, oc) = BASE_SLOT_OFFSETS[index]
        (tr + or) to (tc + oc)
    }
    position < LudoEngine.PLAYER_PATH_LENGTH -> RING_CELLS[LudoEngine.globalIndexOf(color, position)]
    else -> homeColumnCells(color)[position - LudoEngine.PLAYER_PATH_LENGTH]
}

private fun cellFor(token: Token): Pair<Int, Int> = cellFor(token.color, token.index, token.position)

// ludo_board.jpg has no track of its own, only corner bases, a centre medallion and
// gold-railed channels through the middle, so the grid is laid over it with two
// visual adjustments: home columns shift half a cell to run down the channels and
// end on the medallion, and base tokens sit in the image's slot circles.

/** Base-slot rows/columns mapped onto the image's slot circles, in grid units. */
private val BASE_SLOT_GRID = mapOf(3 to 2.45f, 5 to 3.68f, 10 to 11.3f, 12 to 12.52f)

/** The centre of a cell in grid units (multiply by the cell size for pixels). */
private fun gridPoint(cell: Pair<Int, Int>): Offset {
    val (row, col) = cell
    val slotX = BASE_SLOT_GRID[col]
    val slotY = BASE_SLOT_GRID[row]
    if (slotX != null && slotY != null) return Offset(slotX, slotY)
    val (homeRow, homeCol) = HOME_CELL
    val inHomeColumn = (row == homeRow && col in 2..12) || (col == homeCol && row in 2..12)
    return if (inHomeColumn) Offset(col + 0.5f, row + 0.5f) else Offset(col.toFloat(), row.toFloat())
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

// Track tiles are drawn to match the board image's navy slate and gold trim in both themes.
private val TileFace = Color(0xFF2B3447)
private val TileEdge = Color(0xFF151A24)
private val TileTrim = Color(0xFFB08A3A)
private val BoardNavy = Color(0xFF1B2232)

/**
 * Which corner each colour's base square occupies, as (column, row) with 0 = left/top
 * and 1 = right/bottom: [boardCorner] is where the game puts it (next to its start
 * square), [imageCorner] is where ludo_board.jpg painted it. The image's diagonals
 * pair the colours differently from the turn order, so squares are redrawn to fit.
 */
private fun boardCorner(color: LudoColor): Pair<Int, Int> = when (color) {
    LudoColor.RED -> 0 to 0
    LudoColor.GREEN -> 1 to 0
    LudoColor.YELLOW -> 1 to 1
    LudoColor.BLUE -> 0 to 1
}

private fun imageCorner(color: LudoColor): Pair<Int, Int> = when (color) {
    LudoColor.RED -> 0 to 0
    LudoColor.YELLOW -> 1 to 0
    LudoColor.BLUE -> 1 to 1
    LudoColor.GREEN -> 0 to 1
}

/** A base square plus a little surrounding background, as fractions of the image's width. */
private const val BASE_BAND_START = 0.1025f
private const val BASE_BAND_END = 0.6895f
private const val BASE_BAND_SIZE = 0.208f

/** How much of ludo_token.png's width the disc itself covers. */
private const val TOKEN_DISC_FRACTION = 0.724f
private const val TOKEN_BITMAP_PX = 192

/**
 * ludo_token.png in each player's colour. Only the red body is recoloured (hue
 * swapped, saturation/brightness matched to the target), so the gold rim and the
 * white highlights stay as painted.
 */
@Composable
private fun rememberTokenImages(): Map<LudoColor, ImageBitmap> {
    val resources = LocalContext.current.resources
    return remember(resources) {
        val source = BitmapFactory.decodeResource(resources, R.drawable.ludo_token)
        val scaled = Bitmap.createScaledBitmap(source, TOKEN_BITMAP_PX, TOKEN_BITMAP_PX, true)
        LudoEngine.PLAYER_ORDER.associateWith { tintToken(scaled, it).asImageBitmap() }
    }
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
    val boardImage = ImageBitmap.imageResource(R.drawable.ludo_board)
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
            drawBoard(image = boardImage, players = game.players)
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
                        image = tokenImages.getValue(token.color),
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

private fun DrawScope.drawBoard(image: ImageBitmap, players: List<LudoColor>) {
    val cell = size.width / GRID
    fun center(rc: Pair<Int, Int>) = gridPoint(rc) * cell

    drawImage(
        image = image,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.High
    )

    // Base squares: each colour's square from the image, redrawn in that colour's corner,
    // and dimmed under a navy scrim when that colour isn't playing.
    val scale = size.width / image.width
    fun band(corner: Int) = if (corner == 0) BASE_BAND_START else BASE_BAND_END
    val bandPx = (BASE_BAND_SIZE * image.width).roundToInt()
    for (color in LudoEngine.PLAYER_ORDER) {
        val (sx, sy) = imageCorner(color)
        val (dx, dy) = boardCorner(color)
        val dst = Offset(band(dx) * image.width, band(dy) * image.width) * scale
        val dstSize = IntSize((bandPx * scale).roundToInt(), (bandPx * scale).roundToInt())
        if ((sx to sy) != (dx to dy)) {
            drawImage(
                image = image,
                srcOffset = IntOffset((band(sx) * image.width).roundToInt(), (band(sy) * image.width).roundToInt()),
                srcSize = IntSize(bandPx, bandPx),
                dstOffset = IntOffset(dst.x.roundToInt(), dst.y.roundToInt()),
                dstSize = dstSize,
                filterQuality = FilterQuality.High
            )
        }
        if (color !in players) {
            drawRect(
                color = BoardNavy.copy(alpha = 0.6f),
                topLeft = dst,
                size = Size(dstSize.width.toFloat(), dstSize.height.toFloat())
            )
        }
    }

    // The shared track: slate tiles with gold trim, entry squares in their colour, stars on the other safe squares.
    val entryColors = LudoEngine.PLAYER_ORDER.associateBy { LudoEngine.startIndexFor(it) }
    RING_CELLS.forEachIndexed { index, rc ->
        val entry = entryColors[index]
        if (entry != null) {
            val tint = colorFor(entry)
            drawTile(center(rc), cell, face = lerp(TileFace, tint, 0.8f), edge = lerp(tint, Color.Black, 0.5f))
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = cell * 0.16f, center = center(rc))
        } else {
            drawTile(center(rc), cell, face = TileFace, edge = TileEdge)
            if (index in LudoEngine.SAFE_GLOBAL_INDICES) {
                drawStar(center = center(rc), radius = cell * 0.32f, color = PwGoldLight)
            }
        }
    }

    // Home columns run down the image's railed channels to its medallion, which is home.
    for (color in LudoEngine.PLAYER_ORDER) {
        val tint = colorFor(color)
        homeColumnCells(color).forEach { rc ->
            if (rc != HOME_CELL) {
                drawTile(center(rc), cell, face = lerp(TileFace, tint, 0.85f), edge = lerp(tint, Color.Black, 0.5f))
            }
        }
    }
}

/** One raised, rounded track tile centred on [center]: a darker edge below a face with a gold trim. */
private fun DrawScope.drawTile(center: Offset, cell: Float, face: Color, edge: Color) {
    val inset = cell * 0.05f
    val side = cell - inset * 2
    val topLeft = center - Offset(side / 2f, side / 2f)
    val corner = CornerRadius(cell * 0.2f)
    drawRoundRect(color = edge, topLeft = topLeft, size = Size(side, side), cornerRadius = corner)
    drawRoundRect(color = face, topLeft = topLeft, size = Size(side, side - cell * 0.07f), cornerRadius = corner)
    drawRoundRect(
        color = TileTrim.copy(alpha = 0.75f),
        topLeft = topLeft,
        size = Size(side, side),
        cornerRadius = corner,
        style = Stroke(width = cell * 0.035f)
    )
}

private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else radius * 0.45f
        val angle = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
        val x = center.x + cos(angle) * r
        val y = center.y + sin(angle) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
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
