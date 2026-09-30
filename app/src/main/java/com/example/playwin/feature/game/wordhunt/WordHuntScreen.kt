package com.example.playwin.feature.game.wordhunt

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.wordhunt.PlacedWord
import com.example.playwin.core.domain.wordhunt.WORD_HUNT_TURN_SECONDS
import com.example.playwin.core.domain.wordhunt.cells
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameStatusBanner
import com.example.playwin.feature.game.TurnTimerRing
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
import com.example.playwin.feature.game.wordhunt.online.WordHuntOnlineRoute

/** Entry point for Word Hunt: play the computer, or get paired with a random online player. */
@Composable
fun WordHuntRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<PlayMode?>(null) }
    when (mode) {
        null -> GameModeSelectScreen(title = "Word Hunt", onBack = onBack, onSelectMode = { mode = it })
        PlayMode.COMPUTER -> WordHuntComputerRoute(onBack = { mode = null }, onWin = onWin)
        PlayMode.ONLINE -> WordHuntOnlineRoute(
            onBack = { mode = null },
            onWin = onWin,
            onPlayComputer = { mode = PlayMode.COMPUTER }
        )
    }
}

@Composable
private fun WordHuntComputerRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: WordHuntViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    WordHuntScreen(
        uiState = uiState,
        onBack = onBack,
        onCellTap = viewModel::onCellTap,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordHuntScreen(
    uiState: WordHuntUiState,
    onBack: () -> Unit,
    onCellTap: (row: Int, col: Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Word Hunt · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val banner = when (uiState.result) {
                GameOutcome.WIN -> "You win! Claiming your coupon…"
                GameOutcome.LOSE -> "The computer wins this round"
                GameOutcome.DRAW -> "It's a draw!"
                else -> null
            }
            val humanWon = uiState.result == GameOutcome.WIN
            if (banner != null) {
                val gold = MaterialTheme.colorScheme.primary
                GameStatusBanner(accent = gold) {
                    if (humanWon) {
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
                        text = banner,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                TurnBanner(
                    text = if (uiState.turn == WordHuntSide.HUMAN) "Your turn - find a word" else "Computer is hunting…",
                    secondsLeft = uiState.secondsLeft,
                    note = if (uiState.lastTurnTimedOut) "(previous turn timed out)" else null,
                    isResolving = uiState.isResolving
                )
            }

            ScoreLine(myScore = uiState.humanScore, theirScore = uiState.computerScore, theirLabel = "Computer")

            WordList(
                words = uiState.words,
                myWords = uiState.humanClaims.map { it.word }.toSet(),
                theirWords = uiState.computerClaims.map { it.word }.toSet(),
                modifier = Modifier.padding(top = 12.dp)
            )

            Box(modifier = Modifier.padding(top = 16.dp)) {
                LetterGrid(
                    grid = uiState.grid,
                    words = uiState.words,
                    myWords = uiState.humanClaims.map { it.word }.toSet(),
                    theirWords = uiState.computerClaims.map { it.word }.toSet(),
                    selection = uiState.firstSelection,
                    enabled = uiState.canHunt,
                    onCellTap = onCellTap
                )
                if (humanWon) {
                    CelebrationBurst(
                        colors = listOf(PwGoldDark, PwGoldLight, MaterialTheme.colorScheme.secondary, Color.White),
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            if (uiState.result == GameOutcome.LOSE || uiState.result == GameOutcome.DRAW) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

@Composable
fun TurnBanner(text: String, secondsLeft: Int, note: String?, isResolving: Boolean, modifier: Modifier = Modifier) {
    GameStatusBanner(accent = MaterialTheme.colorScheme.primary, modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            note?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (isResolving) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(16.dp),
                strokeWidth = 2.dp
            )
        }
        TurnTimerRing(secondsLeft = secondsLeft, totalSeconds = WORD_HUNT_TURN_SECONDS)
    }
}

/** Both scores as chips that count up as points land, above the one-line scoring rule. */
@Composable
fun ScoreLine(myScore: Int, theirScore: Int, theirLabel: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ScoreChip(label = "You", score = myScore, accent = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
            ScoreChip(label = theirLabel, score = theirScore, accent = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
        }
        Text(
            text = "A word scores its length + half the seconds left on your clock.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        )
    }
}

@Composable
private fun ScoreChip(label: String, score: Int, accent: Color, modifier: Modifier = Modifier) {
    val shown by animateIntAsState(targetValue = score, animationSpec = tween(500), label = "wordHuntScore")
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, accent.copy(alpha = 0.7f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(accent, RoundedCornerShape(50))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
        Text(
            text = "$shown",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordList(words: List<PlacedWord>, myWords: Set<String>, theirWords: Set<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        words.forEach { placed ->
            val mine = placed.word in myWords
            val theirs = placed.word in theirWords
            val fill by animateColorAsState(
                targetValue = when {
                    mine -> MaterialTheme.colorScheme.tertiary
                    theirs -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.surface
                },
                animationSpec = tween(350),
                label = "wordChipFill"
            )
            val shape = RoundedCornerShape(50)
            Text(
                text = placed.word,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (mine || theirs) TextDecoration.LineThrough else TextDecoration.None,
                color = when {
                    mine -> MaterialTheme.colorScheme.onTertiary
                    theirs -> MaterialTheme.colorScheme.onSecondary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .clip(shape)
                    .background(fill)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/** The letter grid; words you found are highlighted in one colour, your opponent's in another. */
@Composable
fun LetterGrid(
    grid: List<List<Char>>,
    words: List<PlacedWord>,
    myWords: Set<String>,
    theirWords: Set<String>,
    selection: Pair<Int, Int>?,
    enabled: Boolean,
    onCellTap: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = grid.size
    val cols = grid.firstOrNull()?.size ?: 0
    if (rows == 0 || cols == 0) return

    val myCells = words.filter { it.word in myWords }.flatMap { it.cells() }.toSet()
    val theirCells = words.filter { it.word in theirWords }.flatMap { it.cells() }.toSet()

    val mineColor = MaterialTheme.colorScheme.tertiary
    val theirColor = MaterialTheme.colorScheme.secondary
    val tileFace = MaterialTheme.colorScheme.surface
    val tileEdge = MaterialTheme.colorScheme.outline
    val gold = MaterialTheme.colorScheme.primary
    val frameShape = RoundedCornerShape(20.dp)

    // A found word is struck through with a highlighter that sweeps from its
    // first letter to its last; words already found when the grid first
    // appears are simply shown highlighted.
    val sweeps = remember { HashMap<String, Animatable<Float, *>>() }
    words.forEach { placed ->
        key(placed.word) {
            val found = placed.word in myWords || placed.word in theirWords
            val sweep = sweeps.getOrPut(placed.word) { Animatable(if (found) 1f else 0f) }
            LaunchedEffect(found) {
                if (found) sweep.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) else sweep.snapTo(0f)
            }
        }
    }

    val selectionPulse = if (selection != null) {
        rememberInfiniteTransition(label = "wordHuntSelection").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "wordHuntSelectionPulse"
        )
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = frameShape)
            .clip(frameShape)
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.surfaceVariant, lerp(MaterialTheme.colorScheme.surfaceVariant, tileEdge, 0.5f))
                )
            )
            .border(1.5.dp, Brush.linearGradient(listOf(PwGoldDark, PwGoldLight, PwGoldDark)), frameShape)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cols / rows.toFloat())
        ) {
            // The raised letter tiles never change, so they keep their own layer.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()
            ) {
                val cell = size.width / cols
                val inset = cell * 0.06f
                val side = cell - inset * 2
                val corner = CornerRadius(cell * 0.2f)
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        val topLeft = Offset(col * cell + inset, row * cell + inset)
                        drawRoundRect(tileEdge, topLeft, Size(side, side), corner)
                        drawRoundRect(tileFace, topLeft, Size(side, side - cell * 0.07f), corner)
                    }
                }
            }

            // Highlighter strokes over found words, and the ring on the first tapped letter.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = size.width / cols
                fun centerOf(row: Int, col: Int) = Offset((col + 0.5f) * cell, (row + 0.5f) * cell)

                words.forEach { placed ->
                    val progress = sweeps[placed.word]?.value ?: 0f
                    if (progress <= 0f) return@forEach
                    val color = if (placed.word in myWords) mineColor else theirColor
                    val start = centerOf(placed.startRow, placed.startCol)
                    val end = lerp(start, centerOf(placed.endRow, placed.endCol), progress)
                    drawLine(lerp(color, Color.Black, 0.3f), start + Offset(0f, cell * 0.05f), end + Offset(0f, cell * 0.05f), cell * 0.8f, StrokeCap.Round)
                    drawLine(color, start, end, strokeWidth = cell * 0.8f, cap = StrokeCap.Round)
                    drawLine(
                        color = Color.White.copy(alpha = 0.22f),
                        start = start - Offset(0f, cell * 0.2f),
                        end = end - Offset(0f, cell * 0.2f),
                        strokeWidth = cell * 0.16f,
                        cap = StrokeCap.Round
                    )
                }

                if (selection != null && selectionPulse != null) {
                    val pulse = selectionPulse.value
                    val center = centerOf(selection.first, selection.second)
                    drawCircle(gold.copy(alpha = 0.22f + 0.2f * pulse), cell * (0.46f + 0.08f * pulse), center)
                    drawCircle(
                        color = gold,
                        radius = cell * 0.42f,
                        center = center,
                        style = Stroke(width = cell * (0.07f + 0.03f * pulse))
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        for (col in 0 until cols) {
                            val cell = row to col
                            val found = cell in myCells || cell in theirCells
                            val letterColor by animateColorAsState(
                                targetValue = when {
                                    cell in myCells -> MaterialTheme.colorScheme.onTertiary
                                    cell in theirCells -> MaterialTheme.colorScheme.onSecondary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                animationSpec = tween(350),
                                label = "wordHuntLetter"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = enabled) { onCellTap(row, col) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = grid[row][col].toString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (found || selection == cell) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    color = letterColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordHuntScreenPreview() {
    PlayWinTheme {
        WordHuntScreen(
            uiState = WordHuntUiState(),
            onBack = {},
            onCellTap = { _, _ -> },
            onPlayAgain = {}
        )
    }
}
