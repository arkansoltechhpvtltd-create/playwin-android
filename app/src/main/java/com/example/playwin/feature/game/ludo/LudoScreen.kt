package com.example.playwin.feature.game.ludo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.domain.ludo.LudoColor
import com.example.playwin.core.domain.ludo.LudoEngine
import com.example.playwin.core.domain.ludo.LudoState
import com.example.playwin.feature.game.ludo.online.LudoOnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.playwin.core.domain.ludo.Token

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
                Text(
                    text = if (uiState.winner == LUDO_HUMAN) "You win! Claiming your coupon…" else "The computer wins this round",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                TurnBanner(uiState)
            }

            LudoBoard(
                game = uiState.game,
                movableTokens = uiState.movableTokens,
                onTokenTap = onTokenTap,
                modifier = Modifier.padding(top = 16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = (if (uiState.isHumanTurn) "Your turn (Red)" else "Computer's turn (Yellow)") + if (uiState.lastTurnTimedOut) " (previous turn timed out)" else "",
            style = MaterialTheme.typography.titleMedium,
            color = colorFor(uiState.currentColor),
            modifier = Modifier.weight(1f)
        )
        Text(text = "${uiState.secondsLeft}s", style = MaterialTheme.typography.labelLarge)
    }
}

// --- Board geometry: a 15x15 grid with a 52-cell ring track, 4 private home-column
// stripes converging near the centre, and 4 corner base areas. This is a clean,
// internally-consistent layout rather than a pixel replica of a physical board. ---

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

private fun cellFor(token: Token): Pair<Int, Int> = when {
    token.position == LudoEngine.IN_BASE -> {
        val (tr, tc) = baseTopLeft(token.color)
        val (or, oc) = BASE_SLOT_OFFSETS[token.index]
        (tr + or) to (tc + oc)
    }
    token.position < LudoEngine.PLAYER_PATH_LENGTH -> RING_CELLS[LudoEngine.globalIndexOf(token.color, token.position)]
    else -> homeColumnCells(token.color)[token.position - LudoEngine.PLAYER_PATH_LENGTH]
}

@Composable
fun LudoBoard(
    game: LudoState,
    movableTokens: List<Token>,
    onTokenTap: (Token) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(movableTokens) {
                detectTapGestures { offset ->
                    val cell = size.width / GRID.toFloat()
                    val hit = movableTokens.minByOrNull { token ->
                        val (row, col) = cellFor(token)
                        val center = Offset((col + 0.5f) * cell, (row + 0.5f) * cell)
                        (center - offset).getDistance()
                    }
                    if (hit != null) {
                        val (row, col) = cellFor(hit)
                        val center = Offset((col + 0.5f) * cell, (row + 0.5f) * cell)
                        if ((center - offset).getDistance() < cell) onTokenTap(hit)
                    }
                }
            }
    ) {
        val cell = size.width / GRID
        fun topLeftOf(row: Int, col: Int) = Offset(col * cell, row * cell)
        fun centerOf(row: Int, col: Int) = Offset((col + 0.5f) * cell, (row + 0.5f) * cell)

        drawRect(color = Color(0xFFF7F5EF), size = size)

        for (color in LudoEngine.PLAYER_ORDER) {
            val (tr, tc) = baseTopLeft(color)
            drawRect(
                color = colorFor(color).copy(alpha = 0.22f),
                topLeft = topLeftOf(tr, tc),
                size = Size(cell * 5, cell * 5)
            )
        }

        RING_CELLS.forEachIndexed { index, (r, c) ->
            val isSafe = index in LudoEngine.SAFE_GLOBAL_INDICES
            drawRect(
                color = if (isSafe) Color(0xFFFFF3C4) else Color.White,
                topLeft = topLeftOf(r, c),
                size = Size(cell, cell)
            )
            drawRect(color = Color(0xFFCCCCCC), topLeft = topLeftOf(r, c), size = Size(cell, cell), style = Stroke(1f))
        }

        for (color in LudoEngine.PLAYER_ORDER) {
            homeColumnCells(color).forEach { (r, c) ->
                drawRect(color = colorFor(color).copy(alpha = 0.55f), topLeft = topLeftOf(r, c), size = Size(cell, cell))
            }
        }

        for (token in game.tokens) {
            val (row, col) = cellFor(token)
            val isMovable = movableTokens.any { it.color == token.color && it.index == token.index }
            drawCircle(color = colorFor(token.color), radius = cell * 0.32f, center = centerOf(row, col))
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = cell * 0.32f,
                center = centerOf(row, col),
                style = Stroke(width = 1.5f)
            )
            if (isMovable) {
                drawCircle(
                    color = Color.Black,
                    radius = cell * 0.42f,
                    center = centerOf(row, col),
                    style = Stroke(width = 3f)
                )
            }
        }
    }
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
