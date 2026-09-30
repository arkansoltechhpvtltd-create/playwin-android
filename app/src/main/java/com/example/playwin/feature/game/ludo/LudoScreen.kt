package com.example.playwin.feature.game.ludo

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.LocalPwIsDarkTheme
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.domain.ludo.LudoColor
import com.example.playwin.core.domain.ludo.LudoEngine
import com.example.playwin.core.domain.ludo.LudoState
import com.example.playwin.core.domain.ludo.Token
import com.example.playwin.feature.game.ludo.online.LudoOnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
import kotlin.math.PI
import kotlin.math.cos
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
        topBar = { PwTopBar(title = "Ludo · vs Computer", onBack = onBack) }
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

/** A raised pill that carries the turn / result line, tinted from the left by [accent]. */
@Composable
private fun StatusBanner(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.26f), Color.Transparent)))
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun TurnBanner(uiState: LudoUiState, modifier: Modifier = Modifier) {
    val accent = colorFor(uiState.currentColor)
    StatusBanner(accent = accent, modifier = modifier) {
        Canvas(modifier = Modifier.size(26.dp)) {
            drawToken(center = center, radius = size.minDimension * 0.42f, color = accent)
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
        TimerRing(secondsLeft = uiState.secondsLeft, totalSeconds = LUDO_TURN_SECONDS)
    }
}

@Composable
private fun WinnerBanner(humanWon: Boolean, modifier: Modifier = Modifier) {
    val gold = MaterialTheme.colorScheme.primary
    StatusBanner(accent = if (humanWon) gold else colorFor(LUDO_COMPUTER), modifier = modifier) {
        if (humanWon) {
            Icon(imageVector = Icons.Filled.EmojiEvents, contentDescription = null, tint = gold, modifier = Modifier.size(26.dp))
        } else {
            Canvas(modifier = Modifier.size(26.dp)) {
                drawToken(center = center, radius = size.minDimension * 0.42f, color = colorFor(LUDO_COMPUTER))
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

/** The per-turn countdown as a draining ring; it turns to the error colour for the last few seconds. */
@Composable
private fun TimerRing(secondsLeft: Int, totalSeconds: Int, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        targetValue = secondsLeft / totalSeconds.toFloat(),
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "ludoTimer"
    )
    val urgent = secondsLeft <= 5
    val ringColor = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outline
    Box(modifier = modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = inset, size = arcSize, style = Stroke(stroke))
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = inset,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "$secondsLeft",
            style = MaterialTheme.typography.labelLarge,
            color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
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

/** The centre of a cell in grid units (multiply by the cell size for pixels). */
private fun gridPoint(cell: Pair<Int, Int>): Offset = Offset(cell.second.toFloat(), cell.first.toFloat())

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

private class BoardPalette(
    val background: List<Color>,
    val tile: Color,
    val tileEdge: Color,
    val yard: Color,
    val star: Color
)

private val LightBoardPalette = BoardPalette(
    background = listOf(Color(0xFFFBF7EE), Color(0xFFEDE3CC)),
    tile = Color(0xFFFFFFFF),
    tileEdge = Color(0xFFD3C7AB),
    yard = Color(0xFFFFFBF2),
    star = PwGoldLight
)

private val DarkBoardPalette = BoardPalette(
    background = listOf(Color(0xFF1D2430), Color(0xFF11151C)),
    tile = Color(0xFF343D4B),
    tileEdge = Color(0xFF1A1F28),
    yard = Color(0xFF232A35),
    star = PwGoldDark
)

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
    val palette = if (LocalPwIsDarkTheme.current) DarkBoardPalette else LightBoardPalette
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
            drawBoard(palette = palette, players = game.players)
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
                        color = colorFor(token.color),
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

private fun DrawScope.drawBoard(palette: BoardPalette, players: List<LudoColor>) {
    val cell = size.width / GRID
    fun center(rc: Pair<Int, Int>) = gridPoint(rc) * cell

    drawRect(brush = Brush.verticalGradient(palette.background), size = size)

    // Corner yards: a glossy coloured slab, an inset panel, and four recessed slots.
    for (color in LudoEngine.PLAYER_ORDER) {
        val inPlay = color in players
        val base = colorFor(color).let { if (inPlay) it else lerp(it, palette.yard, 0.6f) }
        val (tr, tc) = baseTopLeft(color)
        val topLeft = Offset((tc - 0.5f) * cell + cell * 0.1f, (tr - 0.5f) * cell + cell * 0.1f)
        val side = cell * 4.8f
        val corner = CornerRadius(cell * 0.9f)
        drawRoundRect(
            color = lerp(base, Color.Black, 0.35f),
            topLeft = topLeft + Offset(0f, cell * 0.12f),
            size = Size(side, side),
            cornerRadius = corner
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(lerp(base, Color.White, 0.3f), base, lerp(base, Color.Black, 0.18f)),
                start = topLeft,
                end = topLeft + Offset(side, side)
            ),
            topLeft = topLeft,
            size = Size(side, side),
            cornerRadius = corner
        )
        val panelInset = cell * 0.55f
        drawRoundRect(
            color = palette.yard,
            topLeft = topLeft + Offset(panelInset, panelInset),
            size = Size(side - panelInset * 2, side - panelInset * 2),
            cornerRadius = CornerRadius(cell * 0.6f)
        )
        BASE_SLOT_OFFSETS.forEach { (or, oc) ->
            val slot = center((tr + or) to (tc + oc))
            drawCircle(color = lerp(base, Color.Black, 0.3f), radius = cell * 0.5f, center = slot)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lerp(base, Color.Black, 0.12f), lerp(base, Color.White, 0.25f)),
                    center = slot - Offset(0f, cell * 0.12f),
                    radius = cell * 0.6f
                ),
                radius = cell * 0.43f,
                center = slot + Offset(0f, cell * 0.04f)
            )
        }
    }

    // The shared track: raised tiles, entry squares tinted in their colour, stars on the other safe squares.
    val entryColors = LudoEngine.PLAYER_ORDER.associateBy { LudoEngine.startIndexFor(it) }
    RING_CELLS.forEachIndexed { index, rc ->
        val entry = entryColors[index]
        if (entry != null) {
            val tint = colorFor(entry)
            drawTile(rc, cell, face = lerp(palette.tile, tint, 0.7f), edge = lerp(tint, Color.Black, 0.4f))
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = cell * 0.16f, center = center(rc))
        } else {
            drawTile(rc, cell, face = palette.tile, edge = palette.tileEdge)
            if (index in LudoEngine.SAFE_GLOBAL_INDICES) {
                drawStar(center = center(rc), radius = cell * 0.32f, color = palette.star)
            }
        }
    }

    for (color in LudoEngine.PLAYER_ORDER) {
        val tint = colorFor(color)
        homeColumnCells(color).forEach { rc ->
            if (rc != HOME_CELL) {
                drawTile(rc, cell, face = lerp(tint, Color.White, 0.12f), edge = lerp(tint, Color.Black, 0.4f))
            }
        }
    }

    // Home: a gold medallion where every home column ends.
    val home = center(HOME_CELL)
    drawCircle(color = Color.Black.copy(alpha = 0.25f), radius = cell * 0.78f, center = home + Offset(0f, cell * 0.1f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF1C2), PwGoldDark, PwGoldLight),
            center = home - Offset(cell * 0.25f, cell * 0.3f),
            radius = cell * 1.3f
        ),
        radius = cell * 0.76f,
        center = home
    )
    drawCircle(color = Color(0xFF7A5A14), radius = cell * 0.76f, center = home, style = Stroke(cell * 0.06f))
    drawStar(center = home, radius = cell * 0.42f, color = Color(0xFF7A5A14))
}

/** One raised, rounded track tile: a darker edge peeking out below a lighter face. */
private fun DrawScope.drawTile(rc: Pair<Int, Int>, cell: Float, face: Color, edge: Color) {
    val inset = cell * 0.05f
    val side = cell - inset * 2
    val topLeft = Offset((rc.second - 0.5f) * cell + inset, (rc.first - 0.5f) * cell + inset)
    val corner = CornerRadius(cell * 0.2f)
    drawRoundRect(color = edge, topLeft = topLeft, size = Size(side, side), cornerRadius = corner)
    drawRoundRect(color = face, topLeft = topLeft, size = Size(side, side - cell * 0.07f), cornerRadius = corner)
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
 * A glossy, domed playing piece. [center] is where it stands on the board;
 * [lift] raises the piece off its shadow mid-hop. A non-null [glow] (0..1)
 * marks it as tappable with a breathing halo in [glowColor].
 */
private fun DrawScope.drawToken(
    center: Offset,
    radius: Float,
    color: Color,
    lift: Float = 0f,
    glow: Float? = null,
    glowColor: Color = Color.Transparent
) {
    val airborne = (lift / (radius * 3f)).coerceIn(0f, 1f)
    val r = radius * (1f + (glow ?: 0f) * 0.08f + airborne * 0.15f)
    val body = center - Offset(0f, lift)
    val dark = lerp(color, Color.Black, 0.45f)

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
    drawCircle(color = dark, radius = r, center = body)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(lerp(color, Color.White, 0.45f), color, lerp(color, Color.Black, 0.25f)),
            center = body - Offset(r * 0.3f, r * 0.35f),
            radius = r * 1.5f
        ),
        radius = r * 0.9f,
        center = body
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(lerp(color, Color.White, 0.6f), lerp(color, Color.Black, 0.1f)),
            center = body - Offset(r * 0.15f, r * 0.2f),
            radius = r * 0.7f
        ),
        radius = r * 0.5f,
        center = body
    )
    drawCircle(color = dark.copy(alpha = 0.45f), radius = r * 0.5f, center = body, style = Stroke(width = r * 0.06f))
    drawOval(
        color = Color.White.copy(alpha = 0.6f),
        topLeft = body + Offset(-r * 0.55f, -r * 0.68f),
        size = Size(r * 0.5f, r * 0.28f)
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
