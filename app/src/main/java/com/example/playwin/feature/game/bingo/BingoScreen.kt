package com.example.playwin.feature.game.bingo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.feature.game.GameScaffold
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwGameBingo
import com.example.playwin.core.designsystem.theme.PwGameFourInARow
import com.example.playwin.core.designsystem.theme.PwGameGrid
import com.example.playwin.core.designsystem.theme.PwGameWordHunt
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldInk
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.domain.bingo.BINGO_COLUMN_RANGES
import com.example.playwin.core.domain.bingo.BINGO_FREE_COL
import com.example.playwin.core.domain.bingo.BINGO_FREE_ROW
import com.example.playwin.core.domain.bingo.BINGO_LETTERS
import com.example.playwin.core.domain.bingo.BINGO_SIZE
import com.example.playwin.core.domain.bingo.BingoRules
import com.example.playwin.core.domain.bingo.bingoLetterFor
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameStatusBanner
import com.example.playwin.feature.game.bingo.online.BingoOnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode

/** Entry point for Bingo: play the computer, or get paired with a random online player. */
@Composable
fun BingoRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<PlayMode?>(null) }
    when (mode) {
        null -> GameModeSelectScreen(title = "Bingo", onBack = onBack, onSelectMode = { mode = it })
        PlayMode.COMPUTER -> BingoComputerRoute(onBack = { mode = null }, onWin = onWin)
        PlayMode.ONLINE -> BingoOnlineRoute(
            onBack = { mode = null },
            onWin = onWin,
            onPlayComputer = { mode = PlayMode.COMPUTER }
        )
    }
}

@Composable
private fun BingoComputerRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: BingoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    BingoScreen(
        uiState = uiState,
        onBack = onBack,
        onCallNumber = viewModel::onCallNumber,
        onCallRandom = viewModel::onCallRandom,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BingoScreen(
    uiState: BingoUiState,
    onBack: () -> Unit,
    onCallNumber: (Int) -> Unit,
    onCallRandom: () -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    GameScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Bingo · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        // On a tall screen the computer's card sits beside the controls. On a short one the play
        // area (status, calls, your card, controls) still exactly fits the screen, and the
        // called-number history and the computer's card wait just below it, peeking up, for a scroll.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val roomy = maxHeight >= BINGO_ROOMY_HEIGHT
            val playAreaHeight = if (roomy) maxHeight else maxHeight - BINGO_PEEK
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState(), enabled = !roomy),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .height(playAreaHeight)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val humanWon = uiState.winner == BingoSide.HUMAN
                    val banner = when {
                        humanWon -> "Bingo! Claiming your coupon…"
                        uiState.winner == BingoSide.COMPUTER -> "The computer got bingo first"
                        uiState.turn == BingoSide.HUMAN -> "Your call - tap a number on your card"
                        else -> "Computer is calling…"
                    }
                    val gold = MaterialTheme.colorScheme.primary
                    GameStatusBanner(accent = if (uiState.turn == BingoSide.HUMAN || humanWon) gold else PwGameBingo) {
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
                    LastCallText(uiState.lastCall, uiState.lastCaller?.let { if (it == BingoSide.HUMAN) "You" else "Computer" })

                    if (roomy) CalledNumbersRow(uiState.called, modifier = Modifier.padding(top = 8.dp))

                    BoardSlot(modifier = Modifier.padding(top = if (roomy) 16.dp else 10.dp)) {
                        BingoCardView(
                            card = uiState.myCard,
                            called = uiState.called,
                            onCall = if (uiState.canCall) onCallNumber else null
                        )
                        if (humanWon) {
                            CelebrationBurst(
                                colors = listOf(PwGoldDark, PwGoldLight, Color.White) + LETTER_COLORS,
                                modifier = Modifier.matchParentSize()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = onCallRandom, enabled = uiState.canCall) { Text("Call a random number") }
                                if (uiState.isResolving) {
                                    CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp).size(18.dp), strokeWidth = 2.dp)
                                }
                            }
                            if (uiState.winner == BingoSide.COMPUTER) {
                                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                        if (roomy) {
                            OpponentCard(
                                label = "Computer's card",
                                card = uiState.computerCard,
                                called = uiState.called,
                                cardWidth = 112.dp,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
                if (!roomy) {
                    // The called-number history moves down here too; the last call still shows up top.
                    CalledNumbersRow(uiState.called, modifier = Modifier.padding(horizontal = 16.dp))
                    OpponentCard(
                        label = "Computer's card",
                        card = uiState.computerCard,
                        called = uiState.called,
                        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
                    )
                }
            }
        }
    }
}

/** Below this height the computer's card (and the called history) move to just below the play area. */
private val BINGO_ROOMY_HEIGHT = 700.dp

/** How much of what's below the play area peeks up under it, as a hint to scroll. */
private val BINGO_PEEK = 24.dp

/** One jewel tone per BINGO column, shared by the header tabs and the called balls. */
private val LETTER_COLORS = listOf(PwGameGrid, PwGameFourInARow, PwGoldLight, PwGameWordHunt, PwGameBingo)

private fun colorForNumber(number: Int): Color =
    LETTER_COLORS.getOrElse(BINGO_COLUMN_RANGES.indexOfFirst { number in it }) { PwGoldLight }

// The premium card: deep navy in both themes, gold rims, ivory tiles.
private val NavyTop = Color(0xFF1C2E56)
private val NavyBottom = Color(0xFF0A1530)
private val NavyInk = Color(0xFF14213D)
private val IvoryTop = Color(0xFFFFFCF4)
private val IvoryBottom = Color(0xFFEFE4CB)
private val GoldRim = Brush.linearGradient(listOf(PwGoldDark, PwGoldLight, PwGoldDark))

/** A glossy numbered ball, as drawn from the cage: its column's colour around a white label, in a gold rim. */
@Composable
private fun BingoBall(number: Int, size: Dp, modifier: Modifier = Modifier) {
    val color = colorForNumber(number)
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val r = this.size.minDimension / 2f * 0.92f
                // Soft shadow underneath.
                drawCircle(Color.Black.copy(alpha = 0.14f), r * 1.04f, center + Offset(0f, r * 0.16f))
                drawCircle(Color.Black.copy(alpha = 0.18f), r, center + Offset(0f, r * 0.1f))
                // The gold rim, lit top-left.
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFF0BF), PwGoldDark, PwGoldLight, Color(0xFF7A5A14)),
                        start = center - Offset(r, r),
                        end = center + Offset(r, r)
                    ),
                    radius = r,
                    center = center
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(lerp(color, Color.White, 0.5f), color, lerp(color, Color.Black, 0.4f)),
                        center = center - Offset(r * 0.35f, r * 0.4f),
                        radius = r * 1.6f
                    ),
                    radius = r * 0.88f,
                    center = center
                )
                drawCircle(Color(0xFFFDFBF5), r * 0.6f, center)
                drawCircle(lerp(color, Color.Black, 0.2f), r * 0.6f, center, style = Stroke(width = r * 0.05f))
                // Glaze: a broad soft highlight and a sharp catch-light.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                        center = center - Offset(r * 0.42f, r * 0.48f),
                        radius = r * 0.55f
                    ),
                    radius = r * 0.55f,
                    center = center - Offset(r * 0.42f, r * 0.48f)
                )
                drawCircle(Color.White.copy(alpha = 0.75f), r * 0.12f, center - Offset(r * 0.5f, r * 0.58f))
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = bingoLetterFor(number),
                fontSize = (size.value * 0.17f).sp,
                lineHeight = (size.value * 0.18f).sp,
                fontWeight = FontWeight.Bold,
                color = lerp(color, Color.Black, 0.25f)
            )
            Text(
                text = number.toString(),
                fontSize = (size.value * 0.3f).sp,
                lineHeight = (size.value * 0.3f).sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1C1710)
            )
        }
    }
}

/** The latest call: the ball rolls in and pops each time a new number is called. */
@Composable
fun LastCallText(lastCall: Int?, caller: String?, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(lastCall) {
        if (lastCall != null) {
            pop.snapTo(0f)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f))
        }
    }
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .shadow(elevation = 4.dp, shape = shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(NavyTop, NavyBottom)))
            .border(1.dp, GoldRim, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (lastCall != null && caller != null) {
            BingoBall(
                number = lastCall,
                size = 60.dp,
                modifier = Modifier.graphicsLayer {
                    val p = pop.value
                    scaleX = 0.4f + 0.6f * p
                    scaleY = 0.4f + 0.6f * p
                    rotationZ = (1f - p) * -120f
                    alpha = p.coerceIn(0f, 1f)
                }
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "$caller called",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PwGoldDark
                )
                Text(
                    text = "${bingoLetterFor(lastCall)}-$lastCall",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White
                )
            }
        } else {
            Text(
                text = "No numbers called yet",
                style = MaterialTheme.typography.bodyMedium,
                color = PwGoldDark,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
fun OpponentCard(
    label: String,
    card: List<List<Int>>,
    called: List<Int>,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 180.dp
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BingoCardView(
            card = card,
            called = called,
            onCall = null,
            showHeader = false,
            modifier = Modifier.width(cardWidth).padding(top = 6.dp)
        )
    }
}

@Composable
fun CalledNumbersRow(calledNumbers: List<Int>, modifier: Modifier = Modifier) {
    if (calledNumbers.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(calledNumbers.asReversed(), key = { it }) { number ->
            BingoBall(number = number, size = 38.dp, modifier = Modifier.animateItem())
        }
    }
}

/**
 * A bingo card with every [called] number marked. When [onCall] is non-null,
 * tapping an uncalled number on the card calls it.
 */
@Composable
fun BingoCardView(
    card: List<List<Int>>,
    called: List<Int>,
    onCall: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true
) {
    // [showHeader] is off for the small opponent card, which is drawn more tightly throughout.
    val compact = !showHeader
    val gap = if (compact) 3.dp else 5.dp
    val shape = RoundedCornerShape(if (compact) 14.dp else 20.dp)

    // Completed lines glow - a display nicety only; the win itself is decided by the engine.
    val lineCells = remember(card, called) {
        val marks = BingoRules.marks(card, called)
        BingoRules.LINES.filter { line -> line.all { (r, c) -> marks[r][c] } }.flatten().toSet()
    }
    val linePulse = if (lineCells.isNotEmpty()) {
        rememberInfiniteTransition(label = "bingoLine").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bingoLinePulse"
        )
    } else {
        null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = if (compact) 4.dp else 12.dp, shape = shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(NavyTop, NavyBottom)))
            .border(if (compact) 1.5.dp else 2.dp, GoldRim, shape)
            .padding(if (compact) 3.dp else 5.dp)
            // A fine inner gold rule, set in from the rim.
            .border(0.75.dp, PwGoldDark.copy(alpha = 0.45f), RoundedCornerShape(if (compact) 11.dp else 16.dp))
            .padding(if (compact) 4.dp else 7.dp),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        if (showHeader) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                BINGO_LETTERS.forEachIndexed { index, letter ->
                    val color = LETTER_COLORS[index]
                    Text(
                        text = letter,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.25f), lerp(color, Color.Black, 0.2f))))
                            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.25f), 0.5f to Color.Transparent))
                            .border(1.dp, GoldRim, RoundedCornerShape(10.dp))
                            .padding(vertical = 4.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
        for (row in 0 until BINGO_SIZE) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until BINGO_SIZE) {
                    val isFree = row == BINGO_FREE_ROW && col == BINGO_FREE_COL
                    val number = card[row][col]
                    val isMarked = isFree || number in called
                    BingoCell(
                        number = number,
                        isFree = isFree,
                        isMarked = isMarked,
                        compact = compact,
                        linePulse = linePulse.takeIf { (row to col) in lineCells },
                        onClick = if (onCall != null && !isMarked) ({ onCall(number) }) else null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BingoCell(
    number: Int,
    isFree: Boolean,
    isMarked: Boolean,
    compact: Boolean,
    linePulse: State<Float>?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val tappable = onClick != null
    val shape = RoundedCornerShape(if (compact) 6.dp else 10.dp)

    // The daub stamps down with a little overshoot when a number is called;
    // numbers already marked when the card first appears are just shown marked.
    val daub = remember { Animatable(if (isMarked) 1f else 0f) }
    LaunchedEffect(isMarked) {
        if (isMarked) daub.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f)) else daub.snapTo(0f)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "bingoCellPress"
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(elevation = if (compact) 1.dp else 3.dp, shape = shape)
            .clip(shape)
            // A glossy ivory tile; once daubed it turns navy so the gold stamp stands out.
            .drawBehind {
                val d = daub.value.coerceIn(0f, 1f)
                drawRect(Brush.verticalGradient(listOf(lerp(IvoryTop, NavyTop, d), lerp(IvoryBottom, NavyBottom, d))))
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.3f * (1f - d) + 0.08f * d), 0.5f to Color.Transparent))
            }
            .border(
                width = if (tappable) 1.5.dp else 1.dp,
                color = if (tappable) PwGoldLight else PwGoldDark.copy(alpha = 0.55f),
                shape = shape
            )
            .drawBehind {
                val d = daub.value
                if (d > 0f) drawDaub(progress = d)
                if (linePulse != null) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.5f + 0.5f * linePulse.value),
                        radius = size.minDimension * 0.44f,
                        style = Stroke(width = size.minDimension * (0.05f + 0.03f * linePulse.value))
                    )
                }
            }
            .clickable(
                enabled = tappable,
                interactionSource = interactionSource,
                indication = null
            ) { onClick?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        if (isFree) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Free",
                tint = PwGoldInk,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        } else {
            Text(
                text = number.toString(),
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium,
                fontWeight = if (isMarked || tappable) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isMarked) PwGoldInk else NavyInk
            )
        }
    }
}

/** The gold ink stamp over a called number; [progress] overshoots 1 briefly as it lands. */
private fun DrawScope.drawDaub(progress: Float) {
    val radius = size.minDimension * 0.4f * (1.7f - 0.7f * progress)
    val alpha = progress.coerceIn(0f, 1f)
    drawCircle(Color.Black.copy(alpha = 0.2f * alpha), radius, center + Offset(0f, radius * 0.1f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF0BF), PwGoldDark, PwGoldLight),
            center = center - Offset(radius * 0.3f, radius * 0.35f),
            radius = radius * 1.6f
        ),
        radius = radius,
        center = center,
        alpha = alpha
    )
    drawCircle(Color(0xFF7A5A14).copy(alpha = 0.6f * alpha), radius, style = Stroke(width = radius * 0.08f))
    drawCircle(Color.White.copy(alpha = 0.45f * alpha), radius * 0.18f, center - Offset(radius * 0.45f, radius * 0.5f))
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun BingoScreenPreview() {
    PlayWinTheme {
        BingoScreen(
            uiState = BingoUiState(),
            onBack = {},
            onCallNumber = {},
            onCallRandom = {},
            onPlayAgain = {}
        )
    }
}
