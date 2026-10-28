package com.example.playwin.feature.game.chinesecheckers

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.ui.unit.sp
import com.example.playwin.core.designsystem.theme.PwTableTextMuted
import com.example.playwin.feature.game.GameGoldButton
import com.example.playwin.feature.game.GameSetupOverlay
import com.example.playwin.feature.game.SetupNote
import com.example.playwin.feature.game.SetupPicker
import com.example.playwin.feature.game.GameNote
import com.example.playwin.feature.game.GameStatCard
import com.example.playwin.feature.game.GameTone
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersBoard
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersDifficulty
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersMove
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersPosition
import com.example.playwin.core.domain.chinesecheckers.Hole
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameScaffold
import com.example.playwin.feature.game.chinesecheckers.online.ChineseCheckersOnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Entry point for Chinese Checkers: play the computer, or get paired with a random online player. */
@Composable
fun ChineseCheckersRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<PlayMode?>(null) }
    when (mode) {
        null -> GameModeSelectScreen(title = "Chinese Checkers", onBack = onBack, onSelectMode = { mode = it })
        PlayMode.COMPUTER -> ChineseCheckersComputerRoute(onBack = { mode = null }, onWin = onWin)
        PlayMode.ONLINE -> ChineseCheckersOnlineRoute(
            onBack = { mode = null },
            onWin = onWin,
            onPlayComputer = { mode = PlayMode.COMPUTER }
        )
    }
}

@Composable
private fun ChineseCheckersComputerRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: ChineseCheckersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    ChineseCheckersScreen(
        uiState = uiState,
        onBack = onBack,
        onHoleTap = viewModel::onHoleTap,
        onPlayAgain = viewModel::onPlayAgain,
        onDifficultyChosen = viewModel::onDifficultyChosen,
        onColorChosen = viewModel::onColorChosen,
        onStart = viewModel::onStart
    )
}

@Composable
fun ChineseCheckersScreen(
    uiState: ChineseCheckersUiState,
    onBack: () -> Unit,
    onHoleTap: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
    onDifficultyChosen: (ChineseCheckersDifficulty) -> Unit = {},
    onColorChosen: (PegColor) -> Unit = {},
    onStart: () -> Unit = {}
) {
    GameScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Chinese Checkers · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // A narrow side margin so the star runs nearly the full width; the
                // banner and panel add their own inset to keep the usual 16dp edge.
                .padding(horizontal = BoardSideMargin, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val humanWon = uiState.winner == uiState.humanPlayer
            val myColor = uiState.humanColor
            val inset = Modifier.padding(horizontal = 16.dp - BoardSideMargin)

            @Composable
            fun SeatCard(player: Int, modifier: Modifier) {
                val home = uiState.pegsHome(player)
                GameStatCard(
                    label = if (player == uiState.humanPlayer) "You" else "Computer",
                    value = "$home/${ChineseCheckersBoard.PEGS_PER_PLAYER} home",
                    icon = if (player == uiState.humanPlayer) Icons.Filled.Person else Icons.Filled.SmartToy,
                    highlight = (!uiState.isGameOver && uiState.position.sideToMove == player) || uiState.winner == player,
                    progress = home / ChineseCheckersBoard.PEGS_PER_PLAYER.toFloat(),
                    modifier = modifier
                )
            }

            // Each side's card by its own point - the computer's at the top, yours at the
            // bottom, whichever you chose - with the turn line and the level in the corners left
            // free. All of it scales down together on a short screen.
            BoardSlot(modifier = Modifier.padding(vertical = 4.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = inset, verticalAlignment = Alignment.CenterVertically) {
                        GameNote(
                            text = when {
                                !uiState.started -> "Choose your pegs, then start"
                                humanWon -> "Your point is filled - you win! Claiming your coupon…"
                                uiState.winner != null -> "The computer filled its point - it wins"
                                uiState.isComputerThinking || !uiState.isHumanTurn -> "Computer is moving…"
                                uiState.isResolving -> "Checking the board…"
                                uiState.selected != null && uiState.pendingMoves.isEmpty() -> "That peg is boxed in - pick another"
                                uiState.selected != null -> "Tap a gold spot to move there"
                                else -> "Your move - tap one of your ${myColor.label.lowercase()} pegs"
                            },
                            tone = when {
                                humanWon -> GameTone.WIN
                                uiState.winner != null -> GameTone.BAD
                                else -> GameTone.PLAIN
                            },
                            modifier = Modifier.weight(1f).padding(end = 10.dp, bottom = 8.dp)
                        )
                        SeatCard(uiState.computerPlayer, Modifier.weight(1f))
                    }
                    Box(modifier = Modifier.padding(vertical = 6.dp)) {
                        StarBoard(
                            position = uiState.position,
                            me = uiState.humanPlayer,
                            myColor = myColor,
                            selected = uiState.selected,
                            targets = uiState.legalTargets,
                            lastMove = uiState.lastMove,
                            moveKey = uiState.moveCount,
                            enabled = uiState.canPlay,
                            onHoleTap = onHoleTap
                        )
                        if (humanWon) {
                            CelebrationBurst(
                                colors = listOf(PwGoldDark, PwGoldLight, RubyMid, Color.White),
                                modifier = Modifier.matchParentSize()
                            )
                        }
                        if (!uiState.started) {
                            GameSetupOverlay(onStart = onStart) {
                                SetupPicker(
                                    title = "Play as",
                                    options = PegColor.entries,
                                    label = { it.label },
                                    selected = myColor,
                                    onChoose = onColorChosen,
                                    columns = 2,
                                    swatch = ::pegSwatch
                                )
                                SetupNote(
                                    // Your point is always drawn at the bottom, whichever it is on the board.
                                    "You move first · the computer plays ${myColor.other().label} from the opposite point"
                                )
                            }
                        }
                    }
                    Row(modifier = inset, verticalAlignment = Alignment.CenterVertically) {
                        SeatCard(uiState.humanPlayer, Modifier.weight(1f))
                        LevelPicker(
                            uiState = uiState,
                            onDifficultyChosen = onDifficultyChosen,
                            modifier = Modifier.weight(1f).padding(start = 10.dp)
                        )
                    }
                }
            }

            if (uiState.isGameOver && !humanWon) {
                GameGoldButton(
                    text = "Play again",
                    icon = Icons.Filled.Refresh,
                    onClick = onPlayAgain,
                    modifier = inset.padding(top = 8.dp).fillMaxWidth()
                )
            }
        }
    }
}

/**
 * How strongly the computer plays, beside your seat: a choice until the first
 * move, then shown locked, so nothing changes size mid-game.
 */
@Composable
private fun LevelPicker(
    uiState: ChineseCheckersUiState,
    onDifficultyChosen: (ChineseCheckersDifficulty) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (uiState.canChooseDifficulty) "LEVEL" else "LEVEL · LOCKED",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
            color = PwTableTextMuted,
            maxLines = 1
        )
        Row(
            modifier = Modifier
                .padding(top = 6.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChineseCheckersDifficulty.entries.forEach { difficulty ->
                DifficultyChip(
                    label = difficulty.label,
                    chosen = difficulty == uiState.difficulty,
                    enabled = uiState.canChooseDifficulty,
                    onClick = { onDifficultyChosen(difficulty) }
                )
            }
        }
    }
}

/** A difficulty option: a gold-rimmed pill, filled gold when [chosen]; the others dim once it's locked. */
@Composable
private fun DifficultyChip(label: String, chosen: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium),
        color = if (chosen) NavyPlateBottom else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .alpha(if (enabled || chosen) 1f else 0.4f)
            .clip(shape)
            .background(if (chosen) Brush.verticalGradient(listOf(Color(0xFFFFE9A8), PwGoldDark, PwGoldLight)) else Brush.verticalGradient(listOf(Color(0xFF0E1A33), Color(0xFF0A1428))))
            .border(1.dp, if (chosen) Brush.verticalGradient(listOf(Color(0xFFFFF4CF), Color(0xFF7A5512))) else Brush.verticalGradient(listOf(PwGoldDark.copy(alpha = 0.6f), PwGoldLight.copy(alpha = 0.25f))), shape)
            .selectable(selected = chosen, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

/** One peg on its own, [gold] or ruby. */
@Composable
internal fun PegIcon(gold: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawPeg(center = center, radius = size.minDimension * 0.45f, gold = gold)
    }
}

/** A peg colour's body, for its swatch in the setup panel. */
private fun pegSwatch(color: PegColor): Color = if (color == PegColor.GOLD) PwGoldDark else RubyMid

// --- The board ---

private val SQRT3 = sqrt(3f)

/** Space round the star's outermost holes, in hole spacings: room for the rim and its shadow. */
private const val BOARD_MARGIN = 1.0f

/** The board's margin either side, so the star spans nearly the whole width. */
private val BoardSideMargin = 8.dp

/** The star is 12 hole spacings wide and 16 rows tall (each row √3/2 of a spacing). */
private const val BOARD_WIDTH_UNITS = 12f + 2 * BOARD_MARGIN
private val BOARD_HEIGHT_UNITS = 16f * SQRT3 / 2f + 2 * BOARD_MARGIN
private val BOARD_ASPECT = BOARD_WIDTH_UNITS / BOARD_HEIGHT_UNITS

/** Where hole [hole] sits, as an offset in hole spacings from the board's centre. */
private fun holeOffset(hole: Hole): Offset = Offset(hole.q + hole.r / 2f, hole.r * SQRT3 / 2f)

/**
 * The star, seen by player [me]: their home point at the bottom and their
 * pegs in [myColor], the other side's at the top in the other colour (for
 * player 2 the board is turned round). A navy plate with a gold rim, a well for every hole and the
 * pegs in them; the [selected] peg glows, its [targets] pulse gold, and
 * [lastMove]'s route is traced faintly while the peg hops along it - each
 * time [moveKey] changes.
 */
@Composable
internal fun StarBoard(
    position: ChineseCheckersPosition,
    me: Int,
    selected: Int?,
    targets: List<Int>,
    lastMove: ChineseCheckersMove?,
    moveKey: Int,
    enabled: Boolean,
    onHoleTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    myColor: PegColor = PegColor.GOLD
) {
    val holes = ChineseCheckersBoard.holes
    val goldIsMine = myColor == PegColor.GOLD
    val currentOnTap by rememberUpdatedState(onHoleTap)
    // Player 2 sees the board turned half round, so their home is at the bottom too.
    val view = if (me == 2) -1f else 1f
    fun offset(i: Int): Offset = holeOffset(holes[i]) * view

    // The last move, hop by hop: 0 at the start, one more per hole landed on.
    val hop = remember { Animatable(0f) }
    val shownMove = lastMove
    LaunchedEffect(moveKey) {
        val hops = shownMove?.path?.size ?: 0
        hop.snapTo(0f)
        if (hops > 0) hop.animateTo(hops.toFloat(), tween((hops * CHINESE_CHECKERS_HOP_MILLIS).toInt(), easing = LinearEasing))
    }

    val pulse by rememberInfiniteTransition(label = "targets").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "targetPulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(BOARD_ASPECT)
            .semantics { contentDescription = "Chinese checkers board" }
            .pointerInput(enabled, me) {
                if (!enabled) return@pointerInput
                detectTapGestures { tap ->
                    val unit = size.width / BOARD_WIDTH_UNITS
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    // The nearest hole, if the tap is within about half a spacing of it.
                    val nearest = holes.indices.minBy { (centre + offset(it) * unit - tap).getDistanceSquared() }
                    if ((centre + offset(nearest) * unit - tap).getDistance() < unit * 0.6f) currentOnTap(nearest)
                }
            }
    ) {
        val unit = size.width / BOARD_WIDTH_UNITS
        val centre = Offset(size.width / 2f, size.height / 2f)
        fun at(i: Int): Offset = centre + offset(i) * unit

        drawStarPlate(centre, unit)
        drawHomePoint(mine = true, gold = goldIsMine, centre, unit)
        drawHomePoint(mine = false, gold = !goldIsMine, centre, unit)

        val animating = shownMove != null && hop.value < shownMove.path.size
        shownMove?.let { drawRoute(it, ::at, unit) }

        // Wells, then destinations, then pegs on top.
        holes.indices.forEach { drawWell(at(it), unit) }
        targets.forEach { drawTarget(at(it), unit, pulse) }
        holes.indices.forEach { i ->
            val player = position.cells[i]
            if (player == 0 || (animating && i == shownMove!!.to)) return@forEach
            if (i == selected) drawSelection(at(i), unit)
            drawPeg(at(i), unit * PEG_RADIUS, gold = (player == me) == goldIsMine)
        }

        // The moving peg, hopping from hole to hole along its route.
        if (animating) {
            val move = shownMove!!
            val stops = listOf(move.from) + move.path
            val leg = floor(hop.value).toInt().coerceIn(0, move.path.size - 1)
            val t = hop.value - leg
            val flying = at(stops[leg]) + (at(stops[leg + 1]) - at(stops[leg])) * t
            val lift = sin(PI.toFloat() * t)
            val mine = position.cells[move.to] == me
            drawCircle(Color.Black.copy(alpha = 0.25f), radius = unit * PEG_RADIUS, center = flying + Offset(0f, unit * (0.08f + 0.18f * lift)))
            drawPeg(flying - Offset(0f, unit * 0.22f * lift), unit * PEG_RADIUS * (1f + 0.18f * lift), gold = mine == goldIsMine)
        }
    }
}

private const val PEG_RADIUS = 0.36f
private const val WELL_RADIUS = 0.29f

/**
 * The star's outline: its two big triangles as one shape, every edge [margin]
 * hole spacings out from the outermost holes.
 */
private fun starOutline(centre: Offset, unit: Float, margin: Float): Path {
    // Each big triangle's tips are three holes 8 rows from the middle, and its
    // edges run through the outer holes, half that from the centre - so pushing
    // the edges out by [margin] scales the triangle about the centre.
    val inradius = ChineseCheckersBoard.SIZE * SQRT3 / 2f
    val scale = (inradius + margin) / inradius
    fun triangle(vararg tips: Hole): Path = Path().apply {
        tips.forEachIndexed { i, tip ->
            val p = centre + holeOffset(tip) * unit * scale
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    return Path().apply {
        op(
            triangle(Hole(-8, 4), Hole(4, -8), Hole(4, 4)),
            triangle(Hole(8, -4), Hole(-4, 8), Hole(-4, -4)),
            PathOperation.Union
        )
    }
}

/** The navy star plate with a gold rim and a finer gold inlay inside it. */
private fun DrawScope.drawStarPlate(centre: Offset, unit: Float) {
    val star = starOutline(centre, unit, margin = 0.5f)
    drawPath(star, Color.Black.copy(alpha = 0.35f), style = Stroke(width = unit * 0.5f, join = StrokeJoin.Round))
    drawPath(star, Brush.verticalGradient(listOf(NavyPlateTop, NavyPlateBottom)))
    drawPath(star, Brush.radialGradient(listOf(PwGoldDark.copy(alpha = 0.12f), Color.Transparent), center = centre, radius = unit * 7f))
    drawPath(star, GoldRim, style = Stroke(width = unit * 0.14f, join = StrokeJoin.Round))
    drawPath(starOutline(centre, unit, margin = 0.3f), PwGoldDark.copy(alpha = 0.35f), style = Stroke(width = unit * 0.04f, join = StrokeJoin.Round))
}

/**
 * A home point, tinted [gold] or ruby: the viewer's own at the bottom if
 * [mine], the other side's at the top. Each is where the other player is heading.
 */
private fun DrawScope.drawHomePoint(mine: Boolean, gold: Boolean, centre: Offset, unit: Float) {
    // The three corner holes of the point, then pushed out round them.
    val corners = if (mine) listOf(Hole(-4, 8), Hole(-4, 5), Hole(-1, 5)) else listOf(Hole(4, -8), Hole(4, -5), Hole(1, -5))
    val points = corners.map { centre + holeOffset(it) * unit }
    val middle = Offset(points.sumOf { it.x.toDouble() }.toFloat() / 3f, points.sumOf { it.y.toDouble() }.toFloat() / 3f)
    val path = Path().apply {
        points.forEachIndexed { i, p ->
            val q = middle + (p - middle) * 1.42f
            if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
        }
        close()
    }
    val colour = if (gold) PwGoldDark else RubyMid
    drawPath(path, colour.copy(alpha = 0.13f))
    drawPath(path, colour.copy(alpha = 0.45f), style = Stroke(width = unit * 0.05f, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * 0.18f, unit * 0.12f))))
}

/** A hole: a dark well with a faint gold lip. */
private fun DrawScope.drawWell(at: Offset, unit: Float) {
    val r = unit * WELL_RADIUS
    drawCircle(Brush.radialGradient(listOf(Color(0xFF02050D), WellNavy), center = at + Offset(0f, -r * 0.3f), radius = r * 1.2f), radius = r, center = at)
    drawCircle(PwGoldDark.copy(alpha = 0.32f), radius = r, center = at, style = Stroke(width = unit * 0.035f))
}

/** Where the selected peg can go: a pulsing gold ring with a dot. */
private fun DrawScope.drawTarget(at: Offset, unit: Float, pulse: Float) {
    drawCircle(PwGoldLight.copy(alpha = 0.18f + 0.22f * pulse), radius = unit * (0.40f + 0.05f * pulse), center = at)
    drawCircle(PwGoldLight, radius = unit * 0.36f, center = at, style = Stroke(width = unit * 0.06f))
    drawCircle(PwGoldLight, radius = unit * 0.1f, center = at)
}

/** The picked-up peg: a bright gold halo round it. */
private fun DrawScope.drawSelection(at: Offset, unit: Float) {
    drawCircle(Brush.radialGradient(listOf(PwGoldLight.copy(alpha = 0.55f), Color.Transparent), center = at, radius = unit * 0.75f), radius = unit * 0.75f, center = at)
    drawCircle(PwGoldLight, radius = unit * 0.47f, center = at, style = Stroke(width = unit * 0.07f))
}

/** The last move's route, faint and dashed, from where the peg started through every hole it landed on. */
private fun DrawScope.drawRoute(move: ChineseCheckersMove, at: (Int) -> Offset, unit: Float) {
    val stops = (listOf(move.from) + move.path).map(at)
    val path = Path().apply {
        moveTo(stops[0].x, stops[0].y)
        stops.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(
        path,
        PwGoldLight.copy(alpha = 0.45f),
        style = Stroke(width = unit * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * 0.16f, unit * 0.14f)))
    )
    drawCircle(PwGoldLight.copy(alpha = 0.5f), radius = unit * 0.2f, center = stops[0], style = Stroke(width = unit * 0.05f))
}

/** A polished peg, [gold] or ruby, lit from the top left. */
private fun DrawScope.drawPeg(center: Offset, radius: Float, gold: Boolean) {
    val (light, mid, dark) = if (gold) Triple(GoldPegLight, PwGoldDark, GoldPegDark) else Triple(RubyLight, RubyMid, RubyDark)
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = radius, center = center + Offset(0f, radius * 0.18f))
    drawCircle(
        Brush.radialGradient(listOf(light, mid, dark), center = center + Offset(-radius * 0.35f, -radius * 0.4f), radius = radius * 1.6f),
        radius = radius,
        center = center
    )
    drawCircle(dark.copy(alpha = 0.6f), radius = radius, center = center, style = Stroke(width = radius * 0.08f))
    drawCircle(Color.White.copy(alpha = 0.55f), radius = radius * 0.22f, center = center + Offset(-radius * 0.35f, -radius * 0.38f))
}

// Board and pegs stay the same deep navy and gold in both app themes, like the other premium boards.
private val NavyPlateTop = Color(0xFF1C2E56)
private val NavyPlateBottom = Color(0xFF0A1530)
private val WellNavy = Color(0xFF0D1A36)
private val GoldRim = Brush.linearGradient(listOf(PwGoldDark, PwGoldLight, PwGoldDark))
private val GoldPegLight = Color(0xFFFFEDB0)
private val GoldPegDark = Color(0xFF7A5210)
private val RubyLight = Color(0xFFFF9AA2)
private val RubyMid = Color(0xFFD02A3C)
private val RubyDark = Color(0xFF5E0B17)
