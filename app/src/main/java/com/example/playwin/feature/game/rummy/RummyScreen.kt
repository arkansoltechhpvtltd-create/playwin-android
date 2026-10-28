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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.designsystem.theme.PwTableTextMuted
import com.example.playwin.core.domain.rummy.Card
import com.example.playwin.core.domain.rummy.Rank
import com.example.playwin.core.domain.rummy.Suit
import com.example.playwin.core.domain.rummy.isWild
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameDock
import com.example.playwin.feature.game.GameDockButton
import com.example.playwin.feature.game.GameGoldButton
import com.example.playwin.feature.game.GameNote
import com.example.playwin.feature.game.GameScaffold
import com.example.playwin.feature.game.GameStatCard
import com.example.playwin.feature.game.GameTone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    GameScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Rummy · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameStatCard(
                    label = "Turn",
                    value = when {
                        uiState.hasWon -> "You won"
                        uiState.isGameOver -> "Over"
                        uiState.isPlayerTurn -> "You"
                        else -> "Computer"
                    },
                    icon = Icons.Filled.Person,
                    highlight = (uiState.isPlayerTurn && !uiState.isGameOver) || uiState.hasWon,
                    modifier = Modifier.weight(1f)
                )
                GameStatCard(label = "Deck", value = uiState.game.shoe.size.toString(), icon = Icons.Filled.Layers, modifier = Modifier.weight(1f))
                GameStatCard(label = "Wild", value = rankLabel(uiState.game.wildRank), icon = Icons.Filled.Star, modifier = Modifier.weight(1f))
            }
            StatusNote(uiState)

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp)) {
                Table(
                    uiState = uiState,
                    onDrawFromShoe = onDrawFromShoe,
                    onDrawFromDiscard = onDrawFromDiscard,
                    modifier = Modifier.fillMaxSize()
                )
                if (uiState.hasWon) {
                    CelebrationBurst(
                        colors = listOf(PwGoldDark, PwGoldLight, Color.White, SuitRed),
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            HandRack(
                uiState = uiState,
                onCardTap = onCardTap,
                onMoveCard = onMoveCard,
                modifier = Modifier.padding(top = 10.dp)
            )

            GameDock(modifier = Modifier.padding(top = 10.dp)) {
                // A win moves on to the reward screen by itself; Play again is for the other endings.
                if (uiState.isGameOver && !uiState.hasWon) {
                    GameGoldButton(text = "Play again", icon = Icons.Filled.Refresh, onClick = onPlayAgain, modifier = Modifier.fillMaxWidth())
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GameDockButton(
                            label = "Discard",
                            icon = Icons.AutoMirrored.Filled.CallMade,
                            onClick = onConfirmDiscard,
                            enabled = uiState.canConfirmDiscard,
                            modifier = Modifier.weight(1f)
                        )
                        GameGoldButton(
                            text = "Declare",
                            icon = Icons.Filled.EmojiEvents,
                            onClick = onDeclare,
                            enabled = uiState.canDeclare,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** How long a win stays on screen, with the computer's hand revealed, before moving on to the reward. */
private const val REVEAL_HOLD_MILLIS = 3_500L

@Composable
private fun StatusNote(uiState: RummyUiState) {
    GameNote(
        text = when {
            uiState.hasWon -> "You win! Claiming your coupon…"
            uiState.computerDeclared -> "The computer declared a valid hand - you lose this round"
            uiState.hasLost -> "Invalid declaration - you lose this round"
            uiState.isResolving -> "Checking your declaration…"
            uiState.isPlayerTurn && uiState.canDraw -> "Your turn - draw from the deck or the discard pile"
            uiState.isPlayerTurn && uiState.selectedCard == null -> "Tap a card to discard it, or to declare with it"
            uiState.isPlayerTurn -> "Discard it, or Declare if your sets are complete"
            else -> "Computer is playing…"
        },
        tone = when {
            uiState.hasWon -> GameTone.WIN
            uiState.computerDeclared || uiState.hasLost -> GameTone.BAD
            else -> GameTone.PLAIN
        }
    )
}

/**
 * The card table: navy velvet under a soft lamp, in a gold bevel. The
 * computer sits at the far side with its hand fanned face down; the deck and
 * discard pile lie in the middle. Once the round is over the computer's
 * cards are laid face up in its place.
 */
@Composable
private fun Table(
    uiState: RummyUiState,
    onDrawFromShoe: () -> Unit,
    onDrawFromDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val frame = RoundedCornerShape(28.dp)
    val felt = RoundedCornerShape(23.dp)
    val drawPulse = if (uiState.canDraw) rememberDrawPulse() else null
    BoxWithConstraints(
        modifier = modifier
            .shadow(elevation = 16.dp, shape = frame, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(frame)
            .background(GoldBevel)
            .padding(5.dp)
            .clip(felt)
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(FeltLit, FeltMid, FeltEdge), center = Offset(size.width / 2f, size.height * 0.42f), radius = size.maxDimension * 0.75f))
                // A gold hairline inlaid just inside the rim.
                val inset = 8.dp.toPx()
                drawRoundRect(
                    color = PwGoldDark.copy(alpha = 0.3f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(17.dp.toPx()),
                    style = Stroke(1.dp.toPx())
                )
            }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        val revealed = uiState.revealedOpponentHand
        if (revealed != null) {
            RevealedOpponentHand(cards = revealed, wildRank = uiState.game.wildRank, width = maxWidth)
            return@BoxWithConstraints
        }
        // The piles grow with the table, within reason.
        val pileWidth = min(min(maxWidth * 0.26f, (maxHeight - 110.dp) / CARD_ASPECT), 96.dp).coerceAtLeast(44.dp)
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            ComputerSeat(cards = uiState.game.botHand.size, thinking = uiState.isBotThinking)
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Bottom) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DeckStack(width = pileWidth, enabled = uiState.canDraw, pulse = drawPulse, onClick = onDrawFromShoe)
                    TableLabel("Deck", modifier = Modifier.padding(top = 8.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DiscardPile(
                        pile = uiState.game.discardPile,
                        wildRank = uiState.game.wildRank,
                        width = pileWidth,
                        pulse = drawPulse,
                        onClick = if (uiState.canDraw) onDrawFromDiscard else null
                    )
                    TableLabel("Discard", modifier = Modifier.padding(top = 8.dp))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TableLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
        color = PwGoldDark.copy(alpha = 0.9f),
        maxLines = 1,
        modifier = modifier
    )
}

/** The computer's place at the far side of the table: its hand fanned face down and how many cards it holds. */
@Composable
private fun ComputerSeat(cards: Int, thinking: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OpponentFan(count = cards)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.28f))
                .border(1.dp, PwGoldDark.copy(alpha = if (thinking) 0.8f else 0.25f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Computer · $cards cards",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (thinking) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(start = 8.dp).size(12.dp),
                    strokeWidth = 2.dp,
                    color = PwGoldDark
                )
            }
        }
    }
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
private val CardFaceShade = Color(0xFFF1EAD9)
private val CardEdge = Color(0xFFC9BFA6)
private val FeltLit = Color(0xFF26457F)
private val FeltMid = Color(0xFF172E5C)
private val FeltEdge = Color(0xFF0B1834)
private val BackNavy = Color(0xFF0E1D3E)
private val BackNavyDeep = Color(0xFF07112A)
private val GoldBevel = Brush.linearGradient(listOf(Color(0xFFFFEBB0), PwGoldDark, PwGoldLight, Color(0xFF6E4B10), PwGoldDark))
private val GoldFoil = Brush.linearGradient(listOf(Color(0xFFFFE9A8), PwGoldLight, Color(0xFFFFE9A8), PwGoldLight))

private fun suitColor(suit: Suit): Color = when (suit) {
    Suit.HEARTS, Suit.DIAMONDS -> SuitRed
    Suit.SPADES, Suit.CLUBS -> SuitBlack
}

/** A card is always this much taller than it is wide. */
private const val CARD_ASPECT = 1.4f

private fun cardShape(width: Dp) = RoundedCornerShape(width * 0.14f)

/**
 * A card face, [width] wide: rank and suit in two corners, a large suit in
 * the middle - a framed rank for picture cards. A wild card carries a gold
 * foil frame and star. It pops in when it first appears (after
 * [entranceDelayMillis]) and lifts out of the row while [isSelected].
 * Everything is sized to the card, so it reads the same at any font scale.
 */
@Composable
private fun CardView(
    card: Card,
    isWild: Boolean,
    isSelected: Boolean,
    onClick: (() -> Unit)?,
    width: Dp,
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
    val shape = cardShape(width)
    val density = LocalDensity.current
    fun sized(fraction: Float) = with(density) { (width * fraction).toSp() }

    Box(
        modifier = modifier
            .graphicsLayer {
                val e = entrance.value
                alpha = e.coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * e + 0.06f * lift
                scaleY = 0.6f + 0.4f * e + 0.06f * lift
                translationY = -width.toPx() * 0.22f * lift
            }
            .size(width = width, height = width * CARD_ASPECT)
            .shadow(
                elevation = if (isSelected) 12.dp else 3.dp,
                shape = shape,
                ambientColor = if (isSelected) PwGoldDark else Color.Black,
                spotColor = if (isSelected) PwGoldDark else Color.Black
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(CardFace, CardFaceShade)))
            .drawBehind {
                val glow = pulse?.value
                val radius = width.toPx() * 0.14f
                val (frame, stroke) = when {
                    isSelected -> Brush.linearGradient(listOf(PwGoldDark, PwGoldLight)) to 2.5.dp.toPx()
                    glow != null -> Brush.linearGradient(listOf(PwGoldDark.copy(alpha = 0.5f + 0.5f * glow), PwGoldDark.copy(alpha = 0.5f + 0.5f * glow))) to 2.dp.toPx()
                    isWild -> GoldFoil to 2.5.dp.toPx()
                    else -> Brush.linearGradient(listOf(CardEdge, CardEdge)) to 1.dp.toPx()
                }
                drawRoundRect(
                    brush = frame,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(radius - stroke / 2f),
                    style = Stroke(stroke)
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
                    modifier = Modifier.align(Alignment.Center).size(width * 0.5f)
                )
                Text(
                    text = "JOKER",
                    color = ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = sized(0.18f),
                    lineHeight = sized(0.2f),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = width * 0.09f)
                )
            }
            is Card.Standard -> {
                val rank = rankLabel(card.rank)
                val suit = suitSymbol(card.suit)
                val picture = card.rank == Rank.JACK || card.rank == Rank.QUEEN || card.rank == Rank.KING
                CornerIndex(rank, suit, ink, width, Modifier.align(Alignment.TopStart))
                if (picture) {
                    // A framed panel with the rank set large, for J, Q and K.
                    val panel = RoundedCornerShape(width * 0.08f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = width * 0.08f, bottom = width * 0.08f)
                            .size(width = width * 0.52f, height = width * 0.72f)
                            .clip(panel)
                            .background(Brush.verticalGradient(listOf(ink.copy(alpha = 0.08f), ink.copy(alpha = 0.16f))))
                            .border(1.dp, PwGoldLight.copy(alpha = 0.7f), panel),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = rank,
                            color = ink,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = sized(0.4f),
                            lineHeight = sized(0.42f),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                } else {
                    Text(
                        text = suit,
                        color = ink,
                        fontSize = sized(0.56f),
                        lineHeight = sized(0.58f),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = width * 0.08f, bottom = width * 0.04f)
                    )
                }
                if (isWild) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Wild",
                        tint = PwGoldLight,
                        modifier = Modifier.align(Alignment.TopEnd).padding(width * 0.07f).size(width * 0.22f)
                    )
                }
            }
        }
    }
}

/** A card's corner: the rank over a small suit. */
@Composable
private fun CornerIndex(rank: String, suit: String, ink: Color, width: Dp, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val rankSize = with(density) { (width * 0.3f).toSp() }
    val suitSize = with(density) { (width * 0.22f).toSp() }
    Column(
        modifier = modifier.padding(start = width * 0.08f, top = width * 0.05f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = rank, color = ink, fontWeight = FontWeight.ExtraBold, fontSize = rankSize, lineHeight = rankSize, maxLines = 1, softWrap = false)
        Text(text = suit, color = ink, fontSize = suitSize, lineHeight = suitSize, maxLines = 1, softWrap = false)
    }
}

/** The patterned back of a card: deep navy with a gold frame, lattice and a gold diamond. */
private fun DrawScope.drawCardBack() {
    drawRect(Brush.linearGradient(listOf(BackNavy, BackNavyDeep), Offset.Zero, Offset(size.width, size.height)))
    val inset = size.width * 0.1f
    clipRect(inset, inset, size.width - inset, size.height - inset) {
        val step = size.width * 0.2f
        var x = -size.height
        while (x < size.width) {
            drawLine(PwGoldDark.copy(alpha = 0.28f), Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 1f)
            drawLine(PwGoldDark.copy(alpha = 0.28f), Offset(x + size.height, 0f), Offset(x, size.height), strokeWidth = 1f)
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
    drawPath(diamond, Brush.verticalGradient(listOf(Color(0xFFFFE9A8), PwGoldLight), startY = c.y - d * 1.3f, endY = c.y + d * 1.3f))
    // A soft sheen across the upper half.
    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.1f), 0.5f to Color.Transparent))
}

/** The deck, drawn as a short stack; its top card is what you tap to draw. */
@Composable
private fun DeckStack(width: Dp, enabled: Boolean, pulse: State<Float>?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = cardShape(width)
    Box(modifier = modifier.padding(end = 6.dp, bottom = 6.dp)) {
        for (layer in 3 downTo 1) {
            Box(
                modifier = Modifier
                    .offset(x = (layer * 2).dp, y = (layer * 2).dp)
                    .size(width = width, height = width * CARD_ASPECT)
                    .shadow(elevation = 2.dp, shape = shape)
                    .clip(shape)
                    .drawBehind { drawCardBack() }
            )
        }
        Box(
            modifier = Modifier
                .size(width = width, height = width * CARD_ASPECT)
                .shadow(
                    elevation = if (pulse != null) 10.dp else 4.dp,
                    shape = shape,
                    ambientColor = if (pulse != null) PwGoldDark else Color.Black,
                    spotColor = if (pulse != null) PwGoldDark else Color.Black
                )
                .clip(shape)
                .drawBehind {
                    drawCardBack()
                    pulse?.let {
                        val stroke = 2.5.dp.toPx()
                        drawRoundRect(
                            color = PwGoldDark.copy(alpha = 0.45f + 0.55f * it.value),
                            topLeft = Offset(stroke / 2f, stroke / 2f),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(width.toPx() * 0.14f - stroke / 2f),
                            style = Stroke(stroke)
                        )
                    }
                }
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
        )
    }
}

/** The discard pile: the top card face up with the one before it tucked underneath at an angle. */
@Composable
private fun DiscardPile(
    pile: List<Card>,
    wildRank: Rank,
    width: Dp,
    pulse: State<Float>?,
    onClick: (() -> Unit)?
) {
    val top = pile.lastOrNull()
    val under = pile.getOrNull(pile.size - 2)
    Box(modifier = Modifier.padding(end = 6.dp, bottom = 6.dp)) {
        if (under != null) {
            key(under.id) {
                CardView(
                    card = under,
                    isWild = isWild(under, wildRank),
                    isSelected = false,
                    onClick = null,
                    width = width,
                    modifier = Modifier.graphicsLayer { rotationZ = -8f; alpha = 0.9f }
                )
            }
        }
        if (top != null) {
            // Keyed by card, so each new discard lands with its own entrance.
            key(top.id) {
                CardView(
                    card = top,
                    isWild = isWild(top, wildRank),
                    isSelected = false,
                    onClick = onClick,
                    width = width,
                    pulse = pulse
                )
            }
        } else {
            EmptyPileSlot(width)
        }
    }
}

/** The computer's hand as a fan of small face-down cards - up to seven shown, however many it holds. */
@Composable
private fun OpponentFan(count: Int, modifier: Modifier = Modifier) {
    val shown = count.coerceIn(0, 7)
    val miniShape = RoundedCornerShape(5.dp)
    Box(modifier = modifier.size(width = 150.dp, height = 58.dp), contentAlignment = Alignment.BottomCenter) {
        for (i in 0 until shown) {
            val spread = i - (shown - 1) / 2f
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = spread * 15.dp.toPx()
                        translationY = abs(spread) * abs(spread) * 1.2.dp.toPx()
                        rotationZ = spread * 7f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .size(width = 34.dp, height = 48.dp)
                    .shadow(elevation = 3.dp, shape = miniShape)
                    .clip(miniShape)
                    .drawBehind { drawCardBack() }
            )
        }
    }
}

private val HAND_GAP = 6.dp
private val HAND_ROW_GAP = 10.dp
private const val HAND_PER_ROW = 7

/** The player's hand on a rack panel, under a small header. */
@Composable
private fun HandRack(
    uiState: RummyUiState,
    onCardTap: (Card) -> Unit,
    onMoveCard: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1A2E5A), Color(0xFF0E1C3B))))
            .border(1.dp, Brush.verticalGradient(listOf(PwGoldDark.copy(alpha = 0.6f), PwGoldLight.copy(alpha = 0.15f))), shape)
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = "YOUR HAND",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
                color = PwGoldDark,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Drag to arrange",
                style = MaterialTheme.typography.labelMedium,
                color = PwTableTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DraggableHand(
            hand = uiState.arrangedHand,
            wildRank = uiState.game.wildRank,
            selectedCardId = uiState.selectedCard?.id,
            onCardTap = if (uiState.canDiscard) onCardTap else null,
            onMoveCard = onMoveCard,
            // Room above the cards for a selected one to lift into.
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

/**
 * The player's hand, wrapped into rows of equal slots sized to fill the
 * width. Dragging a card lifts it under the finger while the others slide
 * aside to open its new slot; on release it settles there and the move is
 * committed through [onMoveCard]. Only the on-screen order changes. Tapping
 * still selects a card.
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
        val cardWidth = ((maxWidth - HAND_GAP * (HAND_PER_ROW - 1)) / HAND_PER_ROW).coerceAtMost(64.dp)
        val cardHeight = cardWidth * CARD_ASPECT
        val density = LocalDensity.current
        val grid = with(density) {
            HandGrid(
                perRow = HAND_PER_ROW,
                stepX = (cardWidth + HAND_GAP).toPx(),
                stepY = (cardHeight + HAND_ROW_GAP).toPx()
            )
        }
        val latestHand by rememberUpdatedState(hand)
        val latestGrid by rememberUpdatedState(grid)
        val latestOnMoveCard by rememberUpdatedState(onMoveCard)
        val cardShape = cardShape(cardWidth)

        val draggedFrom = draggedId?.let { id -> hand.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
        val shown = if (draggedFrom != null) HandArrangement.move(hand, draggedFrom, dropIndex) else hand
        // Always room for two rows, so drawing a fourteenth card never shifts the screen.
        val rows = ((hand.size + grid.perRow - 1) / grid.perRow).coerceAtLeast(2)
        // Centre the rows when the cards don't fill the width.
        val sideInset = (maxWidth - (cardWidth * HAND_PER_ROW + HAND_GAP * (HAND_PER_ROW - 1))) / 2

        Box(modifier = Modifier.fillMaxWidth().padding(start = sideInset).height(cardHeight * rows + HAND_ROW_GAP * (rows - 1))) {
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
                        width = cardWidth,
                        entranceDelayMillis = if (dealt[0]) 0 else index * 40,
                        modifier = Modifier
                            .zIndex(if (card.id == draggedId) 1f else 0f)
                            .offset { position.value }
                            .graphicsLayer {
                                scaleX = 1f + 0.1f * dragLift
                                scaleY = 1f + 0.1f * dragLift
                                shadowElevation = 14.dp.toPx() * dragLift
                                shape = cardShape
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

/** The computer's hand, face up on the table, shown only once the round is over. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RevealedOpponentHand(cards: List<Card>, wildRank: Rank, width: Dp) {
    // A hair under the exact fit, so rounding never pushes the seventh card onto a new row.
    val cardWidth = ((width - HAND_GAP * (HAND_PER_ROW - 1)) / HAND_PER_ROW - 1.dp).coerceAtMost(52.dp)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TableLabel("Computer's hand")
        Text(
            text = "The ${cards.size} cards it was holding",
            style = MaterialTheme.typography.bodySmall,
            color = PwTableTextMuted,
            modifier = Modifier.padding(top = 2.dp)
        )
        FlowRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(HAND_GAP, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(HAND_ROW_GAP)
        ) {
            cards.forEachIndexed { index, card ->
                key(card.id) {
                    CardView(
                        card = card,
                        isWild = isWild(card, wildRank),
                        isSelected = false,
                        onClick = null,
                        width = cardWidth,
                        entranceDelayMillis = index * 50
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPileSlot(width: Dp, modifier: Modifier = Modifier) {
    val shape = cardShape(width)
    Box(
        modifier = modifier
            .size(width = width, height = width * CARD_ASPECT)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, PwGoldDark.copy(alpha = 0.35f), shape)
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
