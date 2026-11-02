package com.example.playwin.feature.game.twenty48

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.example.playwin.core.designsystem.theme.PwTableTextMuted
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameDock
import com.example.playwin.feature.game.GameDockButton
import com.example.playwin.feature.game.GameGoldButton
import com.example.playwin.feature.game.GameNote
import com.example.playwin.feature.game.GameStatCard
import com.example.playwin.feature.game.GameTone
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.feature.game.GameScaffold
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.feature.game.BoardSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun Twenty48Route(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: Twenty48ViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    Twenty48Screen(
        uiState = uiState,
        onBack = onBack,
        onSwipe = viewModel::onSwipe,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Twenty48Screen(
    uiState: Twenty48UiState,
    onBack: () -> Unit,
    onSwipe: (SwipeDirection) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tiles = uiState.board.flatten()
    val best = tiles.max()
    GameScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "2048", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameStatCard(label = "Time", value = formatElapsed(uiState.elapsedSeconds), icon = Icons.Filled.Timer, modifier = Modifier.weight(1f))
                GameStatCard(
                    label = "Best",
                    value = best.toString(),
                    icon = Icons.Filled.EmojiEvents,
                    highlight = uiState.hasWon,
                    progress = ladderStep(best) / LADDER.lastIndex.toFloat(),
                    modifier = Modifier.weight(1f)
                )
                GameStatCard(label = "Total", value = tiles.sum().toString(), icon = Icons.Filled.Functions, modifier = Modifier.weight(1f))
            }
            GameNote(
                text = when {
                    uiState.hasWon -> "You reached 2048! Checking for your coupon…"
                    uiState.isGameOver -> "No more moves - the board is locked"
                    else -> "Swipe to slide - matching tiles merge"
                },
                tone = when {
                    uiState.hasWon -> GameTone.WIN
                    uiState.isGameOver -> GameTone.BAD
                    else -> GameTone.PLAIN
                }
            )

            BoardSlot(modifier = Modifier.padding(vertical = 10.dp)) {
                Board(
                    board = uiState.board,
                    enabled = !uiState.hasWon && !uiState.isGameOver,
                    onSwipe = onSwipe
                )
                GameOverPlaque(
                    visible = uiState.isGameOver,
                    best = best,
                    elapsed = formatElapsed(uiState.elapsedSeconds),
                    onPlayAgain = onPlayAgain,
                    modifier = Modifier.matchParentSize()
                )
                if (uiState.hasWon) {
                    CelebrationBurst(
                        colors = listOf(PwGoldDark, PwGoldLight, Color(0xFFFFE395), Color.White),
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            GameDock {
                RoadTo2048(best = best)
                GameDockButton(
                    label = "New game",
                    icon = Icons.Filled.Refresh,
                    enabled = !uiState.hasWon,
                    onClick = onPlayAgain,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** Every tile on the way to the goal, 2 through 2048. */
private val LADDER = List(11) { 2 shl it }

/** How far up [LADDER] a tile is: 0 for a 2, the last index for 2048 or beyond. */
private fun ladderStep(tile: Int): Int = LADDER.indexOfLast { it <= tile }.coerceAtLeast(0)

/**
 * The climb to 2048 as a rail of eleven rungs, one per tile value: the rungs
 * already reached are lit in that tile's colours, the newest one breathes,
 * and the next tile to make is named.
 */
@Composable
private fun RoadTo2048(best: Int, modifier: Modifier = Modifier) {
    val reached = ladderStep(best)
    val next = LADDER.getOrNull(reached + 1)
    val density = LocalDensity.current
    val pulse by rememberInfiniteTransition(label = "roadPulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "roadPulseAlpha"
    )
    Column(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ROAD TO 2048",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                color = PwGoldDark,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (next != null) "Next: $next" else "Goal reached!",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (next != null) PwTableTextMuted else PwGoldDark,
                maxLines = 1
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LADDER.forEachIndexed { step, value ->
                val style = tileStyle(value)
                val lit = step <= reached
                val shape = RoundedCornerShape(6.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .graphicsLayer { alpha = if (step == reached) pulse else 1f }
                        .clip(shape)
                        .background(
                            if (lit) Brush.verticalGradient(listOf(style.top, style.bottom))
                            else Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f)))
                        )
                        .border(1.dp, if (lit) PwGoldDark.copy(alpha = style.rimAlpha) else Color.White.copy(alpha = 0.06f), shape),
                    contentAlignment = Alignment.Center
                ) {
                    // Sized to the rung rather than the font scale: the value is also read out by "Next" above.
                    Text(
                        text = value.toString(),
                        fontSize = with(density) { (if (value >= 1000) 8.dp else 10.dp).toSp() },
                        fontWeight = FontWeight.ExtraBold,
                        color = if (lit) style.text else Color.White.copy(alpha = 0.3f),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/** Laid over the board once it locks up: a dimmed board with the run's result and a way to go again. */
@Composable
private fun GameOverPlaque(
    visible: Boolean,
    best: Int,
    elapsed: String,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.92f),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF060D1C).copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            val plaque = RoundedCornerShape(24.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(20.dp)
                    .shadow(elevation = 16.dp, shape = plaque)
                    .clip(plaque)
                    .background(Brush.verticalGradient(listOf(Color(0xFF1D3262), Color(0xFF0E1C38))))
                    .border(1.5.dp, GoldBevel, plaque)
                    .padding(horizontal = 24.dp, vertical = 22.dp)
            ) {
                Text(
                    text = "No moves left",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = PwGoldDark,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Best tile $best  ·  $elapsed",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PwTableTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
                GameGoldButton(text = "Play again", icon = Icons.Filled.Refresh, onClick = onPlayAgain, modifier = Modifier.padding(top = 18.dp))
            }
        }
    }
}

private val NavyInk = Color(0xFF14213D)
private val GoldBevel = Brush.linearGradient(listOf(Color(0xFFFFEBB0), PwGoldDark, PwGoldLight, Color(0xFF6E4B10), PwGoldDark))

/** A tile's look: a top-to-bottom gradient, its number colour, and how strongly the gold rim shows. */
private class TileStyle(val top: Color, val bottom: Color, val text: Color, val rimAlpha: Float)

private fun tileStyle(value: Int): TileStyle = when (value) {
    2 -> TileStyle(Color(0xFFFBF6EA), Color(0xFFEDE2C8), NavyInk, 0.35f)
    4 -> TileStyle(Color(0xFFF6E7C3), Color(0xFFE5CF98), NavyInk, 0.4f)
    8 -> TileStyle(Color(0xFFF4BE6A), Color(0xFFD9922F), Color.White, 0.45f)
    16 -> TileStyle(Color(0xFFEE9558), Color(0xFFC8652B), Color.White, 0.5f)
    32 -> TileStyle(Color(0xFFEA6B55), Color(0xFFB8392D), Color.White, 0.55f)
    64 -> TileStyle(Color(0xFFD23C4A), Color(0xFF8E1C2B), Color.White, 0.6f)
    128 -> TileStyle(Color(0xFF34B092), Color(0xFF186D5A), Color.White, 0.65f)
    256 -> TileStyle(Color(0xFF2F9AC0), Color(0xFF175B7D), Color.White, 0.7f)
    512 -> TileStyle(Color(0xFF4A7BE0), Color(0xFF233F94), Color.White, 0.75f)
    1024 -> TileStyle(Color(0xFF9468DE), Color(0xFF55339A), Color.White, 0.85f)
    2048 -> TileStyle(Color(0xFFFFE395), Color(0xFFC9962E), Color(0xFF2A1E05), 1f)
    else -> TileStyle(Color(0xFF2C2C30), Color(0xFF0E0E10), PwGoldDark, 1f)
}

private const val SLIDE_MILLIS = 110
private const val POP_MILLIS = 90
private const val SPAWN_MILLIS = 140

/** One tile on screen. It keeps its identity across swipes so it can slide rather than jump. */
private class TileSprite(val id: Int, var value: Int, var row: Int, var col: Int, initialScale: Float) {
    /** The number drawn; a merged tile only shows its doubled value once it has arrived. */
    var shownValue by mutableStateOf(value)

    /** Grid coordinates (col, row) of where it's drawn right now. */
    val at = Animatable(Offset(col.toFloat(), row.toFloat()), Offset.VectorConverter)
    val scale = Animatable(initialScale)

    /** Merged into another tile: it slides underneath it and is then removed. */
    var consumed = false
}

/**
 * Turns successive boards - which only say what value is in each cell - into
 * sliding tiles, by replaying the swipe the player just made on the tiles
 * shown. Display only: it never decides a move. If the replay doesn't lead to
 * the new board less one spawned tile (a new game, say), the tiles are just
 * set out again.
 */
private class Twenty48Animator(initial: List<List<Int>>) {
    val tiles = mutableStateListOf<TileSprite>()
    private var shown = initial
    private var nextId = 0

    /** The last swipe the player made, used for the next board that arrives. */
    var pendingSwipe: SwipeDirection? = null

    private var started = false

    fun show(board: List<List<Int>>, scope: CoroutineScope) {
        if (!started) {
            started = true
            placeAll(board, scope)
            return
        }
        if (board == shown) return
        shown = board
        val swipe = pendingSwipe
        pendingSwipe = null

        // Whatever was still animating finishes where it was heading.
        tiles.removeAll { it.consumed }
        tiles.forEach { tile ->
            tile.shownValue = tile.value
            scope.launch {
                tile.at.snapTo(Offset(tile.col.toFloat(), tile.row.toFloat()))
                tile.scale.snapTo(1f)
            }
        }

        val plan = swipe?.let { plan(it, board) }
        if (plan == null) {
            placeAll(board, scope)
            return
        }

        plan.moves.forEach { move ->
            val tile = move.tile
            tile.row = move.row
            tile.col = move.col
            tile.value = move.value
            tile.consumed = move.consumed
            scope.launch {
                tile.at.animateTo(Offset(move.col.toFloat(), move.row.toFloat()), tween(SLIDE_MILLIS, easing = FastOutSlowInEasing))
                when {
                    move.consumed -> tiles -= tile
                    move.merged -> {
                        tile.shownValue = move.value
                        tile.scale.animateTo(1.12f, tween(POP_MILLIS))
                        tile.scale.animateTo(1f, tween(POP_MILLIS))
                    }
                }
            }
        }
        plan.spawn?.let { (row, col) -> spawn(board[row][col], row, col, SLIDE_MILLIS, scope) }
    }

    private fun placeAll(board: List<List<Int>>, scope: CoroutineScope) {
        tiles.clear()
        for (row in 0 until GRID_SIZE) {
            for (col in 0 until GRID_SIZE) {
                if (board[row][col] != 0) spawn(board[row][col], row, col, delayMillis = 0, scope)
            }
        }
    }

    private fun spawn(value: Int, row: Int, col: Int, delayMillis: Int, scope: CoroutineScope) {
        val tile = TileSprite(nextId++, value, row, col, initialScale = 0f)
        tiles += tile
        scope.launch {
            delay(delayMillis.toLong())
            tile.scale.animateTo(1f, tween(SPAWN_MILLIS, easing = FastOutSlowInEasing))
        }
    }

    private class Move(val tile: TileSprite, val row: Int, val col: Int, val value: Int, val merged: Boolean, val consumed: Boolean)

    private class Plan(val moves: List<Move>, val spawn: Pair<Int, Int>?)

    /** Replays [swipe] on the tiles shown, by the same rule as the board; null if that doesn't lead to [board]. */
    private fun plan(swipe: SwipeDirection, board: List<List<Int>>): Plan? {
        val grid = Array(GRID_SIZE) { arrayOfNulls<TileSprite>(GRID_SIZE) }
        tiles.forEach { grid[it.row][it.col] = it }

        // The k-th cell of a line, counting from the wall the tiles slide towards.
        fun cell(line: Int, k: Int): Pair<Int, Int> = when (swipe) {
            SwipeDirection.LEFT -> line to k
            SwipeDirection.RIGHT -> line to GRID_SIZE - 1 - k
            SwipeDirection.UP -> k to line
            SwipeDirection.DOWN -> GRID_SIZE - 1 - k to line
        }

        val moves = mutableListOf<Move>()
        val result = Array(GRID_SIZE) { IntArray(GRID_SIZE) }
        for (line in 0 until GRID_SIZE) {
            var slot = 0
            var open: Move? = null // the last tile placed in this line, if it can still take a merge
            for (k in 0 until GRID_SIZE) {
                val (row, col) = cell(line, k)
                val tile = grid[row][col] ?: continue
                val target = open
                if (target != null && target.tile.value == tile.value) {
                    val merged = Move(target.tile, target.row, target.col, tile.value * 2, merged = true, consumed = false)
                    moves[moves.indexOf(target)] = merged
                    moves += Move(tile, target.row, target.col, tile.value, merged = false, consumed = true)
                    result[target.row][target.col] = tile.value * 2
                    open = null
                } else {
                    val (toRow, toCol) = cell(line, slot++)
                    val move = Move(tile, toRow, toCol, tile.value, merged = false, consumed = false)
                    moves += move
                    result[toRow][toCol] = tile.value
                    open = move
                }
            }
        }

        var spawn: Pair<Int, Int>? = null
        for (row in 0 until GRID_SIZE) {
            for (col in 0 until GRID_SIZE) {
                val expected = board[row][col]
                if (result[row][col] == expected) continue
                if (result[row][col] != 0 || spawn != null) return null
                spawn = row to col
            }
        }
        return Plan(moves, spawn)
    }
}

@Composable
private fun Board(
    board: List<List<Int>>,
    enabled: Boolean,
    onSwipe: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    val animator = remember { Twenty48Animator(board) }
    val scope = rememberCoroutineScope()
    SideEffect { animator.show(board, scope) }

    val frameShape = RoundedCornerShape(24.dp)
    val wellShape = RoundedCornerShape(19.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(elevation = 18.dp, shape = frameShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(frameShape)
            // A polished gold bevel round a recessed navy well.
            .background(GoldBevel)
            .padding(5.dp)
            .clip(wellShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1630), Color(0xFF15284D), Color(0xFF0E1C38))))
            .drawBehind {
                // The bevel's shadow falling into the well along its top and left edges.
                val shade = 14.dp.toPx()
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), endY = shade))
                drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent), endX = shade))
            }
            .border(1.dp, Color.Black.copy(alpha = 0.5f), wellShape)
            .padding(8.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var totalDrag = Offset.Zero
                detectDragGestures(
                    onDragStart = { totalDrag = Offset.Zero },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        resolveSwipeDirection(totalDrag)?.let { direction ->
                            animator.pendingSwipe = direction
                            onSwipe(direction)
                        }
                    }
                )
            }
    ) {
        val step = maxWidth / GRID_SIZE
        val gap = 5.dp
        val tileSize = step - gap * 2
        val tileShape = RoundedCornerShape(12.dp)
        val lip = 4.dp
        val density = LocalDensity.current
        val stepPx = with(density) { step.toPx() }
        val gapPx = with(density) { gap.toPx() }

        // The empty wells, sunk into the board: shaded at the top, catching light at the foot.
        for (row in 0 until GRID_SIZE) {
            for (col in 0 until GRID_SIZE) {
                Box(
                    modifier = Modifier
                        .size(tileSize)
                        .graphicsLayer {
                            translationX = col * stepPx + gapPx
                            translationY = row * stepPx + gapPx
                        }
                        .clip(tileShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF0A1429), Color(0xFF1A2D55))))
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), PwGoldDark.copy(alpha = 0.18f))),
                            tileShape
                        )
                )
            }
        }

        // Tiles being merged away go underneath the tile they merge into.
        animator.tiles.sortedBy { !it.consumed }.forEach { tile ->
            key(tile.id) {
                val value = tile.shownValue
                val style = tileStyle(value)
                val digits = value.toString().length
                val fontSize = with(density) { (tileSize * if (digits <= 2) 0.42f else if (digits == 3) 0.34f else 0.27f).toSp() }
                Box(
                    modifier = Modifier
                        .size(tileSize)
                        .graphicsLayer {
                            val at = tile.at.value
                            translationX = at.x * stepPx + gapPx
                            translationY = at.y * stepPx + gapPx
                            scaleX = tile.scale.value
                            scaleY = tile.scale.value
                        }
                        .shadow(
                            elevation = if (value >= 2048) 14.dp else if (value >= 128) 8.dp else 5.dp,
                            shape = tileShape,
                            ambientColor = if (value >= 128) style.top else Color.Black,
                            spotColor = if (value >= 128) style.top else Color.Black
                        )
                        .clip(tileShape)
                        // The tile's body: a darker lip along the foot gives it thickness.
                        .background(lipColor(style.bottom))
                        .padding(bottom = lip)
                        .clip(tileShape)
                        .background(Brush.verticalGradient(listOf(style.top, style.bottom)))
                        // A lacquered sheen across the upper half and a bright top edge.
                        .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.32f), 0.45f to Color.White.copy(alpha = 0.06f), 0.55f to Color.Transparent))
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), PwGoldDark.copy(alpha = style.rimAlpha))),
                            tileShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = value.toString(),
                        style = TextStyle(
                            fontSize = fontSize,
                            fontWeight = FontWeight.ExtraBold,
                            color = style.text,
                            shadow = if (style.text == NavyInk) null else Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 4f)
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/** The tile's foot: its base colour taken darker. */
private fun lipColor(base: Color): Color = lerp(base, Color.Black, 0.35f)

private fun resolveSwipeDirection(offset: Offset): SwipeDirection? {
    val threshold = 40f
    if (abs(offset.x) < threshold && abs(offset.y) < threshold) return null
    return if (abs(offset.x) > abs(offset.y)) {
        if (offset.x > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
    } else {
        if (offset.y > 0) SwipeDirection.DOWN else SwipeDirection.UP
    }
}

@Preview
@Composable
private fun Twenty48ScreenPreview() {
    PlayWinTheme {
        Twenty48Screen(
            uiState = Twenty48UiState(board = Twenty48Board.emptyBoardWithStartingTiles()),
            onBack = {},
            onSwipe = {},
            onPlayAgain = {}
        )
    }
}
