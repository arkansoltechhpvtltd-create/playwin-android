package com.example.playwin.feature.game.connectfour

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameStatusBanner
import com.example.playwin.feature.game.TurnTimerRing
import com.example.playwin.feature.game.connectfour.online.Connect4OnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode

/** Entry point for Connect 4: play the computer, or get paired with a random online player. */
@Composable
fun Connect4Route(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<PlayMode?>(null) }
    when (mode) {
        null -> GameModeSelectScreen(title = "Connect 4", onBack = onBack, onSelectMode = { mode = it })
        PlayMode.COMPUTER -> Connect4ComputerRoute(onBack = { mode = null }, onWin = onWin)
        PlayMode.ONLINE -> Connect4OnlineRoute(
            onBack = { mode = null },
            onWin = onWin,
            onPlayComputer = { mode = PlayMode.COMPUTER }
        )
    }
}

@Composable
private fun Connect4ComputerRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: Connect4ViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    Connect4Screen(
        uiState = uiState,
        onBack = onBack,
        onColumnClick = viewModel::onColumnClick,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Connect4Screen(
    uiState: Connect4UiState,
    onBack: () -> Unit,
    onColumnClick: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Connect 4 · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val humanWon = uiState.winner == CONNECT4_HUMAN
            when {
                uiState.isDraw -> ResultBanner(text = "It's a draw!", piece = null)
                humanWon -> ResultBanner(text = "You win! Claiming your coupon…", piece = CONNECT4_HUMAN, trophy = true)
                uiState.winner != null -> ResultBanner(text = "The computer wins this round", piece = CONNECT4_COMPUTER)
                else -> TurnBanner(uiState)
            }

            Box(modifier = Modifier.padding(top = 16.dp)) {
                Connect4Board(
                    board = uiState.board,
                    enabled = uiState.currentPlayer == CONNECT4_HUMAN && !uiState.isGameOver && !uiState.isResolving,
                    onColumnClick = onColumnClick
                )
                if (humanWon) {
                    CelebrationBurst(
                        colors = listOf(PwGoldDark, PwGoldLight, discColor(CONNECT4_COMPUTER), Color.White),
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            if (uiState.isGameOver && uiState.winner != CONNECT4_HUMAN) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

/** Piece 1 is gold, piece 2 is emerald - in both modes, both themes. */
private fun discColor(piece: Int): Color = if (piece == 1) Color(0xFFE7B535) else Color(0xFF1FA587)

@Composable
private fun TurnBanner(uiState: Connect4UiState, modifier: Modifier = Modifier) {
    val accent = discColor(uiState.currentPlayer)
    GameStatusBanner(accent = accent, modifier = modifier) {
        Canvas(modifier = Modifier.size(26.dp)) {
            drawDisc(center = center, radius = size.minDimension * 0.46f, color = accent)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = if (uiState.currentPlayer == CONNECT4_HUMAN) "Your turn" else "Computer is thinking…",
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
        if (uiState.isResolving) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(16.dp),
                strokeWidth = 2.dp
            )
        }
        TurnTimerRing(secondsLeft = uiState.secondsLeft, totalSeconds = CONNECT4_TURN_SECONDS)
    }
}

@Composable
private fun ResultBanner(text: String, piece: Int?, modifier: Modifier = Modifier, trophy: Boolean = false) {
    val gold = MaterialTheme.colorScheme.primary
    val accent = if (trophy || piece == null) gold else discColor(piece)
    GameStatusBanner(accent = accent, modifier = modifier) {
        when {
            trophy -> Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = gold,
                modifier = Modifier.size(26.dp)
            )
            piece != null -> Canvas(modifier = Modifier.size(26.dp)) {
                drawDisc(center = center, radius = size.minDimension * 0.46f, color = accent)
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (trophy || piece != null) 12.dp else 0.dp)
        )
    }
}

/** A dropped disc bounces a couple of times as it lands, like the real thing. */
private val DropBounce = Easing { t ->
    val n = 7.5625f
    val d = 2.75f
    when {
        t < 1f / d -> n * t * t
        t < 2f / d -> (t - 1.5f / d).let { n * it * it + 0.75f }
        t < 2.5f / d -> (t - 2.25f / d).let { n * it * it + 0.9375f }
        else -> (t - 2.625f / d).let { n * it * it + 0.984375f }
    }
}

/**
 * What one slot is showing. [fall] is 0 above the board, 1 at rest in its slot
 * and 2 fallen out of the bottom - which is how the board empties on a rematch.
 */
private class DiscSlot(piece: Int) {
    var piece by mutableIntStateOf(piece)
        private set
    val fall = Animatable(1f)

    suspend fun show(newPiece: Int, row: Int) {
        if (newPiece == piece) return
        if (newPiece != 0) {
            piece = newPiece
            fall.snapTo(0f)
            fall.animateTo(1f, tween(380 + 70 * row, easing = DropBounce))
        } else {
            fall.animateTo(2f, tween(320, easing = FastOutLinearInEasing))
            piece = 0
            fall.snapTo(1f)
        }
    }
}

/** Every cell that is part of a four-in-a-row, for highlighting only - the verdict itself comes from the engine. */
private fun winningCells(board: List<List<Int>>): Set<Pair<Int, Int>> {
    val rows = board.size
    val cols = board.firstOrNull()?.size ?: 0
    val result = mutableSetOf<Pair<Int, Int>>()
    val directions = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val piece = board[row][col]
            if (piece == 0) continue
            for ((dr, dc) in directions) {
                val line = (0 until 4).map { (row + dr * it) to (col + dc * it) }
                if (line.all { (r, c) -> r in 0 until rows && c in 0 until cols && board[r][c] == piece }) {
                    result += line
                }
            }
        }
    }
    return result
}

/** Shared by both the vs-Computer and online Connect 4 screens. */
@Composable
fun Connect4Board(
    board: List<List<Int>>,
    enabled: Boolean,
    onColumnClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = board.size
    val cols = if (board.isNotEmpty()) board[0].size else 0
    if (rows == 0 || cols == 0) return

    val isDark = LocalPwIsDarkTheme.current
    val plate = if (isDark) {
        listOf(Color(0xFF2C384C), Color(0xFF171D28))
    } else {
        listOf(Color(0xFF3A5F99), Color(0xFF1D3861))
    }
    val well = if (isDark) Color(0xFF0B0E14) else Color(0xFF12223C)
    val gold = MaterialTheme.colorScheme.primary
    val frameShape = RoundedCornerShape(22.dp)

    val slots = remember(rows, cols) { List(rows * cols) { DiscSlot(board[it / cols][it % cols]) } }
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            key(row, col) {
                val piece = board[row][col]
                LaunchedEffect(piece) { slots[row * cols + col].show(piece, row) }
            }
        }
    }

    val winning = remember(board) { winningCells(board) }
    val winPulse = if (winning.isNotEmpty()) {
        rememberInfiniteTransition(label = "connect4Win").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "connect4WinPulse"
        )
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = frameShape)
            .clip(frameShape)
            .background(Brush.verticalGradient(plate))
            .border(1.5.dp, Brush.linearGradient(listOf(PwGoldDark, PwGoldLight, PwGoldDark)), frameShape)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cols / rows.toFloat())
        ) {
            // Back: the dark wells and the discs, which fall *behind* the plate.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                val cell = size.width / cols
                drawRect(well)
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        val slot = slots[row * cols + col]
                        if (slot.piece == 0) continue
                        val fall = slot.fall.value
                        val rowPosition = if (fall <= 1f) {
                            -1f + (row + 1f) * fall
                        } else {
                            row + (rows - row) * (fall - 1f)
                        }
                        drawDisc(
                            center = Offset((col + 0.5f) * cell, (rowPosition + 0.5f) * cell),
                            radius = cell * 0.44f,
                            color = discColor(slot.piece)
                        )
                    }
                }
            }

            // Front: the plate with round holes punched through it, in its own
            // offscreen layer so the punch-out doesn't erase the discs behind.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                val cell = size.width / cols
                val hole = cell * 0.4f
                drawRect(Brush.verticalGradient(plate))
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        val center = Offset((col + 0.5f) * cell, (row + 0.5f) * cell)
                        // A lit lower lip and a shadowed upper lip make the hole read as bevelled.
                        drawCircle(Color.White.copy(alpha = 0.16f), hole + cell * 0.035f, center + Offset(0f, cell * 0.025f))
                        drawCircle(Color.Black.copy(alpha = 0.3f), hole + cell * 0.035f, center - Offset(0f, cell * 0.015f))
                        drawCircle(Color.Black, hole, center, blendMode = BlendMode.Clear)
                    }
                }
            }

            if (winPulse != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cell = size.width / cols
                    val pulse = winPulse.value
                    winning.forEach { (row, col) ->
                        if (slots[row * cols + col].fall.value != 1f) return@forEach
                        val center = Offset((col + 0.5f) * cell, (row + 0.5f) * cell)
                        drawCircle(gold.copy(alpha = 0.25f * pulse), cell * 0.5f, center)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.55f + 0.45f * pulse),
                            radius = cell * 0.4f,
                            center = center,
                            style = Stroke(width = cell * (0.05f + 0.03f * pulse))
                        )
                    }
                }
            }

            // Column tap targets: a soft light sweeps down the pressed column.
            Row(modifier = Modifier.fillMaxSize()) {
                for (col in 0 until cols) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val highlight by animateFloatAsState(
                        targetValue = if (pressed) 0.22f else 0f,
                        animationSpec = tween(if (pressed) 60 else 260),
                        label = "connect4ColumnPress"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer { alpha = highlight }
                            .background(Brush.verticalGradient(listOf(Color.White, Color.Transparent)))
                            .clickable(
                                enabled = enabled,
                                interactionSource = interactionSource,
                                indication = null,
                                onClickLabel = "Drop a disc in column ${col + 1}"
                            ) { onColumnClick(col) }
                    )
                }
            }
        }
    }
}

/** A glossy disc with a turned groove, lit from the top-left. */
private fun DrawScope.drawDisc(center: Offset, radius: Float, color: Color) {
    drawCircle(color = lerp(color, Color.Black, 0.45f), radius = radius, center = center)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(lerp(color, Color.White, 0.5f), color, lerp(color, Color.Black, 0.22f)),
            center = center - Offset(radius * 0.35f, radius * 0.4f),
            radius = radius * 1.6f
        ),
        radius = radius * 0.93f,
        center = center
    )
    drawCircle(
        color = lerp(color, Color.Black, 0.3f).copy(alpha = 0.7f),
        radius = radius * 0.62f,
        center = center,
        style = Stroke(width = radius * 0.07f)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.3f),
        radius = radius * 0.62f,
        center = center + Offset(0f, radius * 0.05f),
        style = Stroke(width = radius * 0.04f)
    )
    drawOval(
        color = Color.White.copy(alpha = 0.5f),
        topLeft = center + Offset(-radius * 0.6f, -radius * 0.72f),
        size = Size(radius * 0.55f, radius * 0.28f)
    )
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun Connect4ScreenPreview() {
    PlayWinTheme {
        Connect4Screen(
            uiState = Connect4UiState(),
            onBack = {},
            onColumnClick = {},
            onPlayAgain = {}
        )
    }
}
