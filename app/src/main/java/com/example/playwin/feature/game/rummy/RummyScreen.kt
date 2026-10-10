package com.example.playwin.feature.game.rummy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun RummyRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: RummyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        val couponId = uiState.wonCouponId ?: return@LaunchedEffect
        delay(REVEAL_HOLD_MILLIS) // let the player see the computer's revealed hand first
        onWin(couponId)
    }

    RummyScreen(
        uiState = uiState,
        onBack = onBack,
        onDrawFromShoe = viewModel::onDrawFromShoe,
        onDrawFromDiscard = viewModel::onDrawFromDiscard,
        onCardTap = viewModel::onCardTap,
        onMoveCard = viewModel::onMoveCard,
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
    onMoveCard: (fromIndex: Int, toIndex: Int) -> Unit,
    onConfirmDiscard: () -> Unit,
    onDeclare: () -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    // Bring the revealed hand into view when the round ends.
    LaunchedEffect(uiState.isGameOver) {
        if (!uiState.isGameOver) return@LaunchedEffect
        delay(120) // the panel has to be laid out before maxValue covers it
        scroll.animateScrollTo(scroll.maxValue)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Rummy · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(vertical = 16.dp)
        ) {
            StatusBanner(uiState, modifier = Modifier.padding(horizontal = 16.dp))

            // The table, the hand and (once the round is over) the computer's cards scroll
            // between the fixed status line and buttons whenever they don't all fit.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp) // inside the scroll, so shadows aren't clipped at the sides
            ) {
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
                    text = "Your hand (drag to rearrange; tap a card, then Discard or Declare)",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 18.dp)
                )
                DraggableHand(
                    hand = uiState.arrangedHand,
                    wildRank = uiState.game.wildRank,
                    selectedCardId = uiState.selectedCard?.id,
                    onCardTap = if (uiState.canDiscard) onCardTap else null,
                    onMoveCard = onMoveCard,
                    modifier = Modifier.padding(top = 12.dp)
                )

                uiState.revealedOpponentHand?.let { cards ->
                    RevealedOpponentHand(
                        cards = cards,
                        wildRank = uiState.game.wildRank,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // A win moves on to the reward screen by itself; Play again is for the other endings.
                if (uiState.isGameOver && !uiState.hasWon) {
                    PrimaryButton(text = "Play again", onClick = onPlayAgain)
                } else {
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
            }
        }
    }
}

/** How long a win stays on screen, with the computer's hand revealed, before moving on to the reward. */
private const val REVEAL_HOLD_MILLIS = 3_500L

@Composable
private fun StatusBanner(uiState: RummyUiState, modifier: Modifier = Modifier) {
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
    GameStatusBanner(accent = if (lost) MaterialTheme.colorScheme.error else gold, modifier = modifier) {
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

private val HAND_GAP = 6.dp
private val HAND_ROW_GAP = 10.dp

/**
 * The player's hand, wrapped into rows of equal slots. Dragging a card lifts
 * it under the finger while the others slide aside to open its new slot; on
 * release it settles there and the move is committed through [onMoveCard].
 * Only the on-screen order changes. Tapping still selects a card.
 *
 * Cards present on the first frame are dealt in one after another; a card
 * drawn later just pops in.
 */
@Composable
private fun DraggableHand(
    hand: List<Card>,
    wildRank: Rank,
    selectedCardId: Int?,
    onCardTap: ((Card) -> Unit)?,
    onMoveCard: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dealt = remember { booleanArrayOf(false) }
    val scope = rememberCoroutineScope()
    // The card being moved, and the slot it would drop into. It stays set after
    // release until the committed hand arrives, so nothing flickers back.
    var draggedId by remember { mutableStateOf<Int?>(null) }
    var fingerDown by remember { mutableStateOf(false) }
    var dropIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(hand) {
        if (!fingerDown) draggedId = null
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val grid = with(density) {
            val stepX = (CARD_WIDTH + HAND_GAP).toPx()
            HandGrid(
                perRow = ((constraints.maxWidth + HAND_GAP.toPx()) / stepX).toInt().coerceAtLeast(1),
                stepX = stepX,
                stepY = (CARD_HEIGHT + HAND_ROW_GAP).toPx()
            )
        }
        val latestHand by rememberUpdatedState(hand)
        val latestGrid by rememberUpdatedState(grid)
        val latestOnMoveCard by rememberUpdatedState(onMoveCard)

        val draggedFrom = draggedId?.let { id -> hand.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
        val shown = if (draggedFrom != null) HandArrangement.move(hand, draggedFrom, dropIndex) else hand
        val rows = ((hand.size + grid.perRow - 1) / grid.perRow).coerceAtLeast(1)

        Box(modifier = Modifier.fillMaxWidth().height(CARD_HEIGHT * rows + HAND_ROW_GAP * (rows - 1))) {
            shown.forEachIndexed { index, card ->
                key(card.id) {
                    val slot = IntOffset(grid.slotX(index).roundToInt(), grid.slotY(index).roundToInt())
                    val following = card.id == draggedId && fingerDown
                    val position = remember { Animatable(slot, IntOffset.VectorConverter) }
                    LaunchedEffect(slot, following) {
                        if (!following) position.animateTo(slot, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                    }
                    val dragLift by animateFloatAsState(
                        targetValue = if (following) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                        label = "rummyDragLift"
                    )
                    CardView(
                        card = card,
                        isWild = isWild(card, wildRank),
                        isSelected = selectedCardId == card.id,
                        onClick = onCardTap?.let { tap -> { tap(card) } },
                        entranceDelayMillis = if (dealt[0]) 0 else index * 40,
                        modifier = Modifier
                            .zIndex(if (card.id == draggedId) 1f else 0f)
                            .offset { position.value }
                            .graphicsLayer {
                                scaleX = 1f + 0.1f * dragLift
                                scaleY = 1f + 0.1f * dragLift
                                shadowElevation = 14.dp.toPx() * dragLift
                                shape = CardShape
                            }
                            .pointerInput(card.id) {
                                var x = 0f
                                var y = 0f
                                detectDragGestures(
                                    onDragStart = {
                                        x = position.value.x.toFloat()
                                        y = position.value.y.toFloat()
                                        dropIndex = latestHand.indexOfFirst { it.id == card.id }
                                        draggedId = card.id
                                        fingerDown = true
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        x += amount.x
                                        y += amount.y
                                        scope.launch { position.snapTo(IntOffset(x.roundToInt(), y.roundToInt())) }
                                        dropIndex = latestGrid.nearestSlot(x, y, latestHand.size)
                                    },
                                    onDragEnd = {
                                        fingerDown = false
                                        val from = latestHand.indexOfFirst { it.id == card.id }
                                        if (from >= 0 && from != dropIndex) latestOnMoveCard(from, dropIndex) else draggedId = null
                                    },
                                    onDragCancel = {
                                        fingerDown = false
                                        draggedId = null
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
    SideEffect { dealt[0] = true }
}

/** The computer's hand, face up, shown only once the round is over: navy with a gold frame. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RevealedOpponentHand(cards: List<Card>, wildRank: Rank, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(PwBrandGradientStart, RevealNavy)))
            .border(1.5.dp, Brush.linearGradient(listOf(PwGoldDark, PwGoldLight, PwGoldDark)), shape)
            .padding(16.dp)
    ) {
        Text(
            text = "COMPUTER'S HAND",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
            color = PwGoldDark
        )
        Text(
            text = "The ${cards.size} cards it was holding",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 2.dp)
        )
        FlowRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(HAND_GAP),
            verticalArrangement = Arrangement.spacedBy(HAND_ROW_GAP)
        ) {
            cards.forEachIndexed { index, card ->
                key(card.id) {
                    CardView(
                        card = card,
                        isWild = isWild(card, wildRank),
                        isSelected = false,
                        onClick = null,
                        entranceDelayMillis = index * 50
                    )
                }
            }
        }
    }
}

private val RevealNavy = Color(0xFF14233F)

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
            onMoveCard = { _, _ -> },
            onConfirmDiscard = {},
            onDeclare = {},
            onPlayAgain = {}
        )
    }
}
