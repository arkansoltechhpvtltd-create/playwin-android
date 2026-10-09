package com.example.playwin.feature.game.rummy

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwBrandGradientEnd
import com.example.playwin.core.designsystem.theme.PwBrandGradientStart
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.domain.rummy.Card
import com.example.playwin.core.domain.rummy.Rank
import com.example.playwin.core.domain.rummy.Suit
import com.example.playwin.core.domain.rummy.isWild
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameStatusBanner
import kotlinx.coroutines.delay

@Composable
fun RummyRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: RummyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    RummyScreen(
        uiState = uiState,
        onBack = onBack,
        onDrawFromShoe = viewModel::onDrawFromShoe,
        onDrawFromDiscard = viewModel::onDrawFromDiscard,
        onCardTap = viewModel::onCardTap,
        onConfirmDiscard = viewModel::onConfirmDiscard,
        onDeclare = viewModel::onDeclare,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RummyScreen(
    uiState: RummyUiState,
    onBack: () -> Unit,
    onDrawFromShoe: () -> Unit,
    onDrawFromDiscard: () -> Unit,
    onCardTap: (Card) -> Unit,
    onConfirmDiscard: () -> Unit,
    onDeclare: () -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Rummy · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            StatusBanner(uiState)

            Text(
                text = "Wild joker: ${rankLabel(uiState.game.wildRank)} (any suit) + printed jokers",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            // The table: deck, discard pile and the computer's hand on green felt.
            val drawPulse = if (uiState.canDraw) rememberDrawPulse() else null
            val feltShape = RoundedCornerShape(20.dp)
            Box(modifier = Modifier.padding(top = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 10.dp, shape = feltShape)
                        .clip(feltShape)
                        .background(Brush.verticalGradient(listOf(FeltLight, FeltDark)))
                        .border(1.5.dp, Brush.linearGradient(listOf(PwGoldDark, PwGoldLight, PwGoldDark)), feltShape)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FeltLabel("Deck (${uiState.game.shoe.size})")
                        DeckStack(
                            modifier = Modifier.padding(top = 6.dp),
                            enabled = uiState.canDraw,
                            pulse = drawPulse,
                            onClick = onDrawFromShoe
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FeltLabel("Discard")
                        val top = uiState.game.discardPile.lastOrNull()
                        Box(modifier = Modifier.padding(top = 6.dp)) {
                            if (top != null) {
                                // Keyed by card, so each new discard lands with its own entrance.
                                key(top.id) {
                                    CardView(
                                        card = top,
                                        isWild = isWild(top, uiState.game.wildRank),
                                        isSelected = false,
                                        onClick = if (uiState.canDraw) onDrawFromDiscard else null,
                                        pulse = drawPulse
                                    )
                                }
                            } else {
                                EmptyPileSlot()
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FeltLabel("Computer's hand")
                        OpponentFan(count = uiState.game.botHand.size, modifier = Modifier.padding(top = 6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Text(
                                text = "${uiState.game.botHand.size} cards",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            if (uiState.isBotThinking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(start = 6.dp).size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = PwGoldDark
                                )
                            }
                        }
                    }
                }
                if (uiState.hasWon) {
                    CelebrationBurst(
                        colors = listOf(PwGoldDark, PwGoldLight, Color.White, SuitRed),
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            Text(
                text = "Your hand (tap a card, then Discard or Declare)",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 18.dp)
            )
            // Cards present on the first frame are dealt in one after another;
            // a card drawn later just pops in.
            val dealt = remember { booleanArrayOf(false) }
            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                uiState.game.playerHand
                    .sortedWith(compareBy({ it.sortSuit() }, { it.sortRank() }))
                    .forEachIndexed { index, card ->
                        key(card.id) {
                            CardView(
                                card = card,
                                isWild = isWild(card, uiState.game.wildRank),
                                isSelected = uiState.selectedCard?.id == card.id,
                                onClick = if (uiState.canDiscard) { { onCardTap(card) } } else null,
                                entranceDelayMillis = if (dealt[0]) 0 else index * 40
                            )
                        }
                    }
            }
            SideEffect { dealt[0] = true }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onConfirmDiscard,
                    enabled = uiState.canConfirmDiscard,
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("Discard")
                }
                PrimaryButton(
                    text = "Declare",
                    onClick = onDeclare,
                    enabled = uiState.canDeclare,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                )
            }

            if (uiState.isGameOver) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

private fun Card.sortSuit(): Int = when (this) {
    is Card.PrintedJoker -> 4
    is Card.Standard -> suit.ordinal
}

private fun Card.sortRank(): Int = when (this) {
    is Card.PrintedJoker -> 0
    is Card.Standard -> rank.order
}

@Composable
private fun StatusBanner(uiState: RummyUiState) {
    val text = when {
        uiState.hasWon -> "You win! Claiming your coupon…"
        uiState.computerDeclared -> "The computer declared a valid hand - you lose this round."
        uiState.hasLost -> "Invalid declaration - you lose this round."
        uiState.isPlayerTurn && uiState.canDraw -> "Your turn - draw a card"
        uiState.isPlayerTurn -> "Your turn - discard or declare"
        else -> "Computer's turn…"
    }
    val gold = MaterialTheme.colorScheme.primary
    val lost = uiState.computerDeclared || uiState.hasLost
    GameStatusBanner(accent = if (lost) MaterialTheme.colorScheme.error else gold) {
        if (uiState.hasWon) {
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
        if (uiState.isResolving) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun FeltLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
}

/** The slow gold breath on the deck and discard pile while it's the player's turn to draw. */
@Composable
private fun rememberDrawPulse(): State<Float> =
    rememberInfiniteTransition(label = "rummyDraw").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rummyDrawPulse"
    )

private fun rankLabel(rank: Rank): String = when (rank) {
    Rank.ACE -> "A"
    Rank.JACK -> "J"
    Rank.QUEEN -> "Q"
    Rank.KING -> "K"
    else -> rank.order.toString()
}

/** The trailing variation selector asks for the text form, so a suit is never swapped for its emoji. */
private fun suitSymbol(suit: Suit): String = when (suit) {
    Suit.SPADES -> "♠"
    Suit.HEARTS -> "♥"
    Suit.DIAMONDS -> "♦"
    Suit.CLUBS -> "♣"
} + "︎"

private val SuitRed = Color(0xFFC62828)
private val SuitBlack = Color(0xFF1A1A1A)
private val CardFace = Color(0xFFFFFDF7)
private val CardFaceShade = Color(0xFFF0E9D8)
private val CardEdge = Color(0xFFC9BFA6)
private val FeltLight = Color(0xFF1B6B53)
private val FeltDark = Color(0xFF0B3C2E)

private fun suitColor(suit: Suit): Color = when (suit) {
    Suit.HEARTS, Suit.DIAMONDS -> SuitRed
    Suit.SPADES, Suit.CLUBS -> SuitBlack
}

private val CARD_WIDTH = 48.dp
private val CARD_HEIGHT = 66.dp
private val CardShape = RoundedCornerShape(8.dp)

/**
 * A card face: rank and suit in the corner, a large suit in the middle. A wild
 * card carries a gold frame and star. It pops in when it first appears (after
 * [entranceDelayMillis]) and lifts out of the row while [isSelected].
 */
@Composable
private fun CardView(
    card: Card,
    isWild: Boolean,
    isSelected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    entranceDelayMillis: Int = 0,
    pulse: State<Float>? = null
) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(entranceDelayMillis.toLong())
        entrance.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 420f))
    }
    val lift by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "rummyCardLift"
    )
    val ink = when (card) {
        is Card.PrintedJoker -> PwGoldLight
        is Card.Standard -> suitColor(card.suit)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val e = entrance.value
                alpha = e.coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * e + 0.06f * lift
                scaleY = 0.6f + 0.4f * e + 0.06f * lift
                translationY = -10.dp.toPx() * lift
            }
            .size(width = CARD_WIDTH, height = CARD_HEIGHT)
            .shadow(elevation = if (isSelected) 8.dp else 3.dp, shape = CardShape)
            .clip(CardShape)
            .background(Brush.verticalGradient(listOf(CardFace, CardFaceShade)))
            .drawBehind {
                val glow = pulse?.value
                val frame = when {
                    isSelected -> PwGoldLight
                    glow != null -> PwGoldDark.copy(alpha = 0.5f + 0.5f * glow)
                    isWild -> PwGoldLight
                    else -> CardEdge
                }
                val width = (if (isSelected || isWild || glow != null) 2.dp else 1.dp).toPx()
                drawRoundRect(
                    color = frame,
                    topLeft = Offset(width / 2f, width / 2f),
                    size = Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(8.dp.toPx() - width / 2f),
                    style = Stroke(width)
                )
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        when (card) {
            is Card.PrintedJoker -> {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                )
                Text(
                    text = "JOKER",
                    color = ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp)
                )
            }
            is Card.Standard -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 5.dp, top = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = rankLabel(card.rank),
                        color = ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        lineHeight = 16.sp
                    )
                    Text(text = suitSymbol(card.suit), color = ink, fontSize = 10.sp, lineHeight = 10.sp)
                }
                Text(
                    text = suitSymbol(card.suit),
                    color = ink,
                    fontSize = 24.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 5.dp, bottom = 4.dp)
                )
                if (isWild) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Wild",
                        tint = PwGoldLight,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(11.dp)
                    )
                }
            }
        }
    }
}

/** The patterned back of a card: deep navy-to-emerald with a gold frame and lattice. */
private fun DrawScope.drawCardBack() {
    drawRect(Brush.linearGradient(listOf(PwBrandGradientStart, PwBrandGradientEnd), Offset.Zero, Offset(size.width, size.height)))
    val inset = size.width * 0.1f
    clipRect(inset, inset, size.width - inset, size.height - inset) {
        val step = size.width * 0.2f
        var x = -size.height
        while (x < size.width) {
            drawLine(PwGoldDark.copy(alpha = 0.3f), Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 1f)
            drawLine(PwGoldDark.copy(alpha = 0.3f), Offset(x + size.height, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
    }
    drawRoundRect(
        color = PwGoldDark,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(size.width * 0.08f),
        style = Stroke(width = size.width * 0.03f)
    )
    val c = center
    val d = size.width * 0.17f
    val diamond = Path().apply {
        moveTo(c.x, c.y - d * 1.3f)
        lineTo(c.x + d, c.y)
        lineTo(c.x, c.y + d * 1.3f)
        lineTo(c.x - d, c.y)
        close()
    }
    drawPath(diamond, PwBrandGradientStart)
    drawPath(diamond, PwGoldDark, style = Stroke(width = size.width * 0.04f))
}

/** The deck, drawn as a short stack; its top card is what you tap to draw. */
@Composable
private fun DeckStack(enabled: Boolean, pulse: State<Float>?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(end = 4.dp, bottom = 4.dp)) {
        for (layer in 2 downTo 1) {
            Box(
                modifier = Modifier
                    .offset(x = (layer * 2).dp, y = (layer * 2).dp)
                    .size(width = CARD_WIDTH, height = CARD_HEIGHT)
                    .clip(CardShape)
                    .drawBehind { drawCardBack() }
            )
        }
        Box(
            modifier = Modifier
                .size(width = CARD_WIDTH, height = CARD_HEIGHT)
                .shadow(elevation = 3.dp, shape = CardShape)
                .clip(CardShape)
                .drawBehind {
                    drawCardBack()
                    pulse?.let {
                        val width = 2.dp.toPx()
                        drawRoundRect(
                            color = PwGoldDark.copy(alpha = 0.5f + 0.5f * it.value),
                            topLeft = Offset(width / 2f, width / 2f),
                            size = Size(size.width - width, size.height - width),
                            cornerRadius = CornerRadius(8.dp.toPx() - width / 2f),
                            style = Stroke(width)
                        )
                    }
                }
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
        )
    }
}

/** The computer's hand as a fan of small face-down cards - up to five shown, however many it holds. */
@Composable
private fun OpponentFan(count: Int, modifier: Modifier = Modifier) {
    val shown = count.coerceIn(0, 5)
    val miniShape = RoundedCornerShape(4.dp)
    Box(modifier = modifier.size(width = 76.dp, height = 46.dp), contentAlignment = Alignment.BottomCenter) {
        for (i in 0 until shown) {
            val spread = i - (shown - 1) / 2f
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = spread * 11.dp.toPx()
                        translationY = kotlin.math.abs(spread) * 2.dp.toPx()
                        rotationZ = spread * 9f
                    }
                    .size(width = 28.dp, height = 40.dp)
                    .shadow(elevation = 2.dp, shape = miniShape)
                    .clip(miniShape)
                    .drawBehind { drawCardBack() }
            )
        }
    }
}

@Composable
private fun EmptyPileSlot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = CARD_WIDTH, height = CARD_HEIGHT)
            .clip(CardShape)
            .background(Color.Black.copy(alpha = 0.18f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CardShape)
    )
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun RummyScreenPreview() {
    PlayWinTheme {
        RummyScreen(
            uiState = RummyUiState(),
            onBack = {},
            onDrawFromShoe = {},
            onDrawFromDiscard = {},
            onCardTap = {},
            onConfirmDiscard = {},
            onDeclare = {},
            onPlayAgain = {}
        )
    }
}
