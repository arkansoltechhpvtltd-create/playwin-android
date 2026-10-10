package com.example.playwin.feature.game.twenty48

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Scaffold
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.feature.game.GameStatusBanner
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
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "2048", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val statusText = when {
                uiState.hasWon -> "You reached 2048! Claiming your coupon…"
                uiState.isGameOver -> "No more moves left"
                else -> "Swipe to merge tiles"
            }
            GameStatusBanner(accent = MaterialTheme.colorScheme.primary) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatChip(label = "Time", value = formatElapsed(uiState.elapsedSeconds), modifier = Modifier.weight(1f))
                StatChip(label = "Best tile", value = uiState.board.flatten().max().toString(), modifier = Modifier.weight(1f))
            }

            BoardSlot(modifier = Modifier.padding(top = 16.dp)) {
                Board(
                    board = uiState.board,
                    enabled = !uiState.hasWon && !uiState.isGameOver,
                    onSwipe = onSwipe
                )
            }

            if (uiState.isGameOver) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private val NavyTop = Color(0xFF16264A)
private val NavyBottom = Color(0xFF0A1428)
private val NavyWell = Color(0xFF1C2E52)
private val NavyInk = Color(0xFF14213D)
private val GoldRim = Brush.linearGradient(listOf(PwGoldDark, PwGoldLight, PwGoldDark))

/** A navy plaque with a gold rim: a small gold label over a bold white value. */
@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .shadow(elevation = 4.dp, shape = shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(NavyTop, NavyBottom)))
            .border(1.dp, GoldRim, shape)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp),
            color = PwGoldDark
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}

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

    val frameShape = RoundedCornerShape(22.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(elevation = 12.dp, shape = frameShape)
            .clip(frameShape)
            .background(Brush.verticalGradient(listOf(NavyTop, NavyBottom)))
            .border(2.dp, GoldRim, frameShape)
            .padding(10.dp)
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
        val density = LocalDensity.current
        val stepPx = with(density) { step.toPx() }
        val gapPx = with(density) { gap.toPx() }

        // The empty wells.
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
                        .background(NavyWell)
                        .border(1.dp, PwGoldDark.copy(alpha = 0.12f), tileShape)
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
                        .shadow(elevation = if (value >= 2048) 10.dp else 4.dp, shape = tileShape, ambientColor = style.bottom, spotColor = style.bottom)
                        .clip(tileShape)
                        .background(Brush.verticalGradient(listOf(style.top, style.bottom)))
                        // A soft sheen across the upper half, as if lacquered.
                        .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 0.5f to Color.Transparent))
                        .border(1.5.dp, PwGoldDark.copy(alpha = style.rimAlpha), tileShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = value.toString(),
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        color = style.text,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

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
