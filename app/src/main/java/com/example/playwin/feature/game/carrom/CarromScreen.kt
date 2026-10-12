package com.example.playwin.feature.game.carrom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.feature.game.GameScaffold
import com.example.playwin.R
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.domain.carrom.CarromBoard
import com.example.playwin.core.domain.carrom.Coin
import com.example.playwin.core.domain.carrom.CoinColor
import com.example.playwin.core.domain.carrom.QUEEN_ID
import com.example.playwin.core.domain.carrom.STRIKER_ID
import com.example.playwin.core.domain.carrom.Vec2
import com.example.playwin.core.domain.carrom.coinColorOf
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameStatusBanner
import com.example.playwin.feature.game.PhysicsFrameClock
import com.example.playwin.feature.game.TurnTimerRing
import com.example.playwin.feature.game.carrom.online.CarromOnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Entry point for Carrom: play the computer, or get paired with a random online player. */
@Composable
fun CarromRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<PlayMode?>(null) }
    when (mode) {
        null -> GameModeSelectScreen(title = "Carrom", onBack = onBack, onSelectMode = { mode = it })
        PlayMode.COMPUTER -> CarromComputerRoute(onBack = { mode = null }, onWin = onWin)
        PlayMode.ONLINE -> CarromOnlineRoute(
            onBack = { mode = null },
            onWin = onWin,
            onPlayComputer = { mode = PlayMode.COMPUTER }
        )
    }
}

@Composable
private fun CarromComputerRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: CarromViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    PhysicsFrameClock(onTick = viewModel::onPhysicsTick)

    CarromScreen(
        uiState = uiState,
        onBack = onBack,
        onShoot = viewModel::onShoot,
        onPlaceStriker = viewModel::onPlaceStriker,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarromScreen(
    uiState: CarromUiState,
    onBack: () -> Unit,
    onShoot: (direction: Vec2, power: Float) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
    onPlaceStriker: ((x: Float) -> Unit)? = null
) {
    GameScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Carrom · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val score = "${uiState.scores[CARROM_HUMAN]}-${uiState.scores[CARROM_COMPUTER]}"
            val humanWon = uiState.winner == CARROM_HUMAN
            when {
                uiState.isDraw -> StatusBanner(text = "It's a draw! $score")
                humanWon -> StatusBanner(text = "You win $score! Claiming your coupon…", trophy = true)
                uiState.winner == CARROM_COMPUTER -> StatusBanner(text = "The computer wins $score")
                else -> TurnBanner(
                    text = when {
                        uiState.isComputerThinking -> "Computer is lining up a shot…"
                        uiState.currentPlayer == CARROM_HUMAN -> "Your shot - slide the striker, then drag back"
                        else -> "Computer's shot"
                    } + buildString {
                        if (uiState.lastTurnTimedOut) append(" (previous turn timed out)")
                        if (uiState.fouled) append(" (foul on previous shot)")
                    },
                    secondsLeft = uiState.secondsLeft.takeIf { uiState.currentPlayer == CARROM_HUMAN },
                    isBusy = uiState.isPiecesMoving || uiState.isResolving || uiState.isComputerThinking
                )
            }

            BoardSlot(modifier = Modifier.padding(top = 16.dp)) {
                CarromBoardView(
                    coins = uiState.coins,
                    canAim = uiState.canAim,
                    onShoot = onShoot,
                    onPlaceStriker = onPlaceStriker
                )
                if (humanWon) {
                    CelebrationBurst(
                        colors = listOf(PwGoldDark, PwGoldLight, QueenFace, WhiteFace, MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            ScoreLegend(
                myColor = uiState.playerColors[CARROM_HUMAN],
                myScore = uiState.scores.getValue(CARROM_HUMAN),
                theirScore = uiState.scores.getValue(CARROM_COMPUTER),
                theirLabel = "Computer",
                modifier = Modifier.padding(top = 12.dp)
            )

            if (uiState.isGameOver && uiState.winner != CARROM_HUMAN) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

@Composable
fun TurnBanner(text: String, secondsLeft: Int?, isBusy: Boolean, modifier: Modifier = Modifier) {
    GameStatusBanner(accent = MaterialTheme.colorScheme.primary, modifier = modifier) {
        Canvas(modifier = Modifier.size(26.dp)) {
            drawCarromPiece(center = center, radius = size.minDimension * 0.44f, piece = StrikerPiece)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        )
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = if (secondsLeft != null) 10.dp else 0.dp)
                    .size(16.dp),
                strokeWidth = 2.dp
            )
        }
        if (secondsLeft != null) {
            TurnTimerRing(secondsLeft = secondsLeft, totalSeconds = CARROM_TURN_SECONDS)
        }
    }
}

@Composable
private fun StatusBanner(text: String, modifier: Modifier = Modifier, trophy: Boolean = false) {
    val gold = MaterialTheme.colorScheme.primary
    GameStatusBanner(accent = gold, modifier = modifier) {
        if (trophy) {
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
    }
}

/** The scoreboard under the board: each side's coin colour (once claimed) and coins potted. */
@Composable
fun ScoreLegend(myColor: CoinColor?, myScore: Int, theirScore: Int, theirLabel: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScoreChip(label = "You", score = myScore, color = myColor, modifier = Modifier.weight(1f))
            ScoreChip(label = theirLabel, score = theirScore, color = myColor?.other(), modifier = Modifier.weight(1f))
        }
        if (myColor == null) {
            Text(
                text = "Board open - first pot picks your color",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun ScoreChip(label: String, score: Int, color: CoinColor?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (color != null) {
            Canvas(modifier = Modifier.size(22.dp)) {
                drawCarromPiece(center = center, radius = size.minDimension * 0.44f, piece = pieceFor(color))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (color != null) 8.dp else 0.dp)
        )
        Text(
            text = "$score",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private val FrameDark = Color(0xFF34190B)
private val LineInk = Color(0xFF3E2412)
private val LineRed = Color(0xFFB0342C)
private val QueenFace = Color(0xFFD2304A)
private val WhiteFace = Color(0xFFF4EBD6)

/** The flat colours of one turned wooden piece: its edge, its face and the groove cut into it. */
private class PieceStyle(val rim: Color, val face: Color, val groove: Color)

private val WhitePiece = PieceStyle(rim = Color(0xFFB3A27F), face = WhiteFace, groove = Color(0xFFCBBB9A))
private val BlackPiece = PieceStyle(rim = Color(0xFF0C0C0E), face = Color(0xFF2B2B30), groove = Color(0xFF4C4C55))
private val QueenPiece = PieceStyle(rim = Color(0xFF7E1526), face = QueenFace, groove = Color(0xFFF3B9C2))
private val StrikerPiece = PieceStyle(rim = Color(0xFF9A8C70), face = Color(0xFFFDFBF5), groove = PwGoldLight)

private fun pieceFor(color: CoinColor): PieceStyle = when (color) {
    CoinColor.WHITE -> WhitePiece
    CoinColor.BLACK -> BlackPiece
    CoinColor.QUEEN -> QueenPiece
}

private fun pieceFor(id: Int): PieceStyle = if (id == STRIKER_ID) StrikerPiece else pieceFor(coinColorOf(id))

/** A piece that has just dropped: it slides from where it was into the pocket, shrinking and fading. */
private class SinkingPiece(val id: Int, val from: Vec2, val pocket: Vec2) {
    val progress = Animatable(0f)
}

/** What a drag on the board is doing, decided by its first movement. */
private enum class DragMode { UNDECIDED, SLIDE, AIM }

/** How far from the striker's centre, in striker radii, a drag still counts as grabbing it. */
private const val STRIKER_GRAB_RADII = 2.5f

/**
 * The board. Drag back from the striker to aim; release to shoot. When
 * [onPlaceStriker] is given, a drag that starts on the striker and moves
 * sideways slides it along the baseline instead (the caller keeps it on the
 * line and in bounds). With [flipped] the board is turned 180° so a
 * top-baseline player (online player 2) shoots "up" from the bottom of their
 * screen - aiming still reports directions in board space, since the
 * rotation applies to touch input as well as drawing.
 */
@Composable
fun CarromBoardView(
    coins: List<Coin>,
    canAim: Boolean,
    onShoot: (Vec2, Float) -> Unit,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
    onPlaceStriker: ((x: Float) -> Unit)? = null
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    val scale = if (canvasSize.width > 0) canvasSize.width / CarromBoard.SIZE else 0f
    val striker = coins.firstOrNull { it.id == STRIKER_ID && !it.pocketed }
    val strikerScreenPos = striker?.let { Offset(it.position.x * scale, it.position.y * scale) }

    // The gesture below outlives recompositions, so it reads these through state.
    val currentStriker = rememberUpdatedState(striker)
    val currentStrikerScreenPos = rememberUpdatedState(strikerScreenPos)
    val currentScale = rememberUpdatedState(scale)
    val currentOnShoot = rememberUpdatedState(onShoot)
    val currentPlaceStriker = rememberUpdatedState(onPlaceStriker)

    val maxDragPx = canvasSize.width * 0.4f

    // Spot pieces that were on the board last frame and are pocketed now, and let them sink.
    val sinking = remember { mutableStateListOf<SinkingPiece>() }
    val previous = remember { arrayOf(coins) }
    val scope = rememberCoroutineScope()
    SideEffect {
        val before = previous[0]
        if (before !== coins) {
            previous[0] = coins
            if (coins.count { it.pocketed } > before.count { it.pocketed }) {
                coins.filter { it.pocketed }.forEach { coin ->
                    val last = before.firstOrNull { it.id == coin.id && !it.pocketed } ?: return@forEach
                    val pocket = CarromBoard.corners.minBy { (it - last.position).length }
                    val piece = SinkingPiece(coin.id, last.position, pocket)
                    sinking += piece
                    scope.launch {
                        piece.progress.animateTo(1f, tween(300, easing = FastOutLinearInEasing))
                        sinking -= piece
                    }
                }
            }
        }
    }

    val strikerPulse = if (canAim) {
        rememberInfiniteTransition(label = "carromStriker").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "carromStrikerPulse"
        )
    } else {
        null
    }
    val gold = MaterialTheme.colorScheme.primary
    val frameShape = RoundedCornerShape(6.dp)
    // Light falls from the top of the *screen*, whichever way the board is turned.
    val lightDir = if (flipped) -1f else 1f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(elevation = 12.dp, shape = frameShape)
            .clip(frameShape)
            .background(FrameDark)
    ) {
        // The artwork stays upright when the board is turned for player 2 - it's
        // symmetrical, and its lighting then still falls from the top of the screen.
        // Coil decodes carrom_board straight to the size it's drawn at: as a plain
        // drawable it would otherwise be scaled up by the screen density first.
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(R.drawable.carrom_board).build(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.High,
            modifier = Modifier.matchParentSize()
        )
        // The play area sits exactly on the artwork's playing surface, inside its rail.
        // The pockets and baselines are the artwork's too: CarromBoard measures them
        // off the same image, so nothing here draws them.
        Box(
            modifier = Modifier
                .padding(start = maxWidth * CarromBoard.ART_SURFACE_START, top = maxWidth * CarromBoard.ART_SURFACE_START)
                .size(maxWidth * CarromBoard.ART_SURFACE_SIZE)
                .graphicsLayer { rotationZ = if (flipped) 180f else 0f }
                .onSizeChanged { canvasSize = it }
                // Keyed on canAim only: the striker moves while it's being slid, and
                // restarting the gesture on every move would cancel the drag.
                .pointerInput(canAim) {
                    if (!canAim) return@pointerInput
                    var dragStart = Offset.Zero
                    var strikerStartX = 0f
                    var mode = DragMode.UNDECIDED
                    fun reset() {
                        dragCurrent = null
                        mode = DragMode.UNDECIDED
                    }
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStart = offset
                            strikerStartX = currentStriker.value?.position?.x ?: 0f
                            mode = DragMode.UNDECIDED
                        },
                        onDrag = { change, dragAmount ->
                            if (mode == DragMode.UNDECIDED) {
                                // A drag that starts on the striker and heads sideways slides it
                                // along the baseline; anything else aims, as before.
                                val place = currentPlaceStriker.value
                                val strikerAt = currentStrikerScreenPos.value
                                val grabRadius = CarromBoard.STRIKER_RADIUS * currentScale.value * STRIKER_GRAB_RADII
                                val onStriker = strikerAt != null && (dragStart - strikerAt).getDistance() <= grabRadius
                                mode = if (place != null && onStriker && abs(dragAmount.x) > abs(dragAmount.y)) DragMode.SLIDE else DragMode.AIM
                            }
                            when (mode) {
                                DragMode.SLIDE -> {
                                    val s = currentScale.value
                                    if (s > 0f) currentPlaceStriker.value?.invoke(strikerStartX + (change.position.x - dragStart.x) / s)
                                }
                                else -> dragCurrent = change.position
                            }
                        },
                        onDragEnd = {
                            val start = currentStrikerScreenPos.value
                            val end = dragCurrent
                            // Shot thresholds from this element's own size (maxDragPx above is for drawing).
                            val maxDrag = size.width * 0.4f
                            val minDrag = size.width * 0.02f
                            if (mode == DragMode.AIM && start != null && end != null) {
                                val pullDistance = (end - start).getDistance()
                                if (pullDistance > minDrag && maxDrag > 0f) {
                                    val direction = Vec2(start.x - end.x, start.y - end.y).normalizedOrZero()
                                    val power = (pullDistance / maxDrag).coerceIn(0f, 1f)
                                    currentOnShoot.value(direction, power)
                                }
                            }
                            reset()
                        },
                        onDragCancel = { reset() }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val s = size.width / CarromBoard.SIZE
                fun toScreen(v: Vec2) = Offset(v.x * s, v.y * s)

                sinking.forEach { piece ->
                    val t = piece.progress.value
                    val at = piece.from + (piece.pocket - piece.from) * t
                    drawCarromPiece(
                        center = toScreen(at),
                        radius = CarromBoard.radiusOf(piece.id) * s * (1f - 0.55f * t),
                        piece = pieceFor(piece.id),
                        lightDir = lightDir,
                        alpha = 1f - t
                    )
                }

                coins.forEach { coin ->
                    if (coin.pocketed) return@forEach
                    val center = toScreen(coin.position)
                    val radius = CarromBoard.radiusOf(coin.id) * s
                    if (coin.id == STRIKER_ID && strikerPulse != null && dragCurrent == null) {
                        val pulse = strikerPulse.value
                        drawCircle(gold.copy(alpha = 0.16f + 0.2f * pulse), radius * (1.5f + 0.3f * pulse), center)
                        drawCircle(
                            color = gold.copy(alpha = 0.6f + 0.4f * pulse),
                            radius = radius * 1.28f,
                            center = center,
                            style = Stroke(width = radius * 0.12f)
                        )
                    }
                    drawCarromPiece(center = center, radius = radius, piece = pieceFor(coin.id), lightDir = lightDir)
                    if (coin.id == QUEEN_ID) {
                        drawCircle(PwGoldDark, radius * 0.6f, center, style = Stroke(width = radius * 0.1f))
                    }
                }

                val start = strikerScreenPos
                val end = dragCurrent
                if (start != null && end != null && canAim) {
                    val pull = end - start
                    val pullDistance = pull.getDistance()
                    val power = if (maxDragPx > 0f) (pullDistance / maxDragPx).coerceIn(0f, 1f) else 0f
                    val direction = Vec2(start.x - end.x, start.y - end.y).normalizedOrZero()
                    val strikerRadius = CarromBoard.STRIKER_RADIUS * s

                    // The pull-back: a taut band from the striker to the finger.
                    drawLine(
                        color = LineInk.copy(alpha = 0.55f),
                        start = start,
                        end = end,
                        strokeWidth = 3f * s,
                        cap = StrokeCap.Round
                    )
                    drawCircle(LineInk.copy(alpha = 0.55f), 6f * s, end)

                    // The shot: a trail of dots that thins out, longer the harder the pull.
                    val aimLength = (60f + power * 190f) * s
                    val dots = 9
                    for (i in 1..dots) {
                        val f = i / dots.toFloat()
                        drawCircle(
                            color = Color.White.copy(alpha = 0.95f - 0.6f * f),
                            radius = (5.5f - 3f * f) * s,
                            center = Offset(
                                start.x + direction.x * (strikerRadius + aimLength * f),
                                start.y + direction.y * (strikerRadius + aimLength * f)
                            )
                        )
                    }

                    // The power gauge wraps the striker and runs from gold to red.
                    val gauge = Size(strikerRadius * 3.2f, strikerRadius * 3.2f)
                    val gaugeTopLeft = start - Offset(strikerRadius * 1.6f, strikerRadius * 1.6f)
                    drawArc(
                        color = Color.Black.copy(alpha = 0.22f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = gaugeTopLeft,
                        size = gauge,
                        style = Stroke(width = 5f * s)
                    )
                    drawArc(
                        color = lerp(PwGoldDark, LineRed, power),
                        startAngle = -90f,
                        sweepAngle = 360f * power,
                        useCenter = false,
                        topLeft = gaugeTopLeft,
                        size = gauge,
                        style = Stroke(width = 5f * s, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

/**
 * One turned wooden piece seen from above: a soft shadow, a darker edge, the
 * face, a cut groove and a catch-light. Drawn with flat colours only, since
 * every piece is redrawn each physics frame. [lightDir] is -1 when the board
 * is rotated 180°, so the shadow still falls toward the bottom of the screen.
 */
private fun DrawScope.drawCarromPiece(
    center: Offset,
    radius: Float,
    piece: PieceStyle,
    lightDir: Float = 1f,
    alpha: Float = 1f
) {
    drawCircle(Color.Black.copy(alpha = 0.3f * alpha), radius * 1.04f, center + Offset(0f, radius * 0.2f * lightDir))
    drawCircle(piece.rim.copy(alpha = alpha), radius, center)
    drawCircle(piece.face.copy(alpha = alpha), radius * 0.88f, center - Offset(0f, radius * 0.04f * lightDir))
    drawCircle(piece.groove.copy(alpha = alpha), radius * 0.56f, center, style = Stroke(width = radius * 0.1f))
    drawCircle(piece.groove.copy(alpha = alpha), radius * 0.2f, center)
    drawArc(
        color = Color.White.copy(alpha = 0.5f * alpha),
        startAngle = if (lightDir > 0f) 200f else 20f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = center - Offset(radius * 0.74f, radius * 0.74f),
        size = Size(radius * 1.48f, radius * 1.48f),
        style = Stroke(width = radius * 0.1f, cap = StrokeCap.Round)
    )
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun CarromScreenPreview() {
    PlayWinTheme {
        CarromScreen(
            uiState = CarromUiState(),
            onBack = {},
            onShoot = { _, _ -> },
            onPlayAgain = {}
        )
    }
}
