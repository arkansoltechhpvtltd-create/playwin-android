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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersBoard
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersDifficulty
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersMove
import com.example.playwin.core.domain.chinesecheckers.Hole
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameScaffold
import com.example.playwin.feature.game.GameStatusBanner
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Entry point for Chinese Checkers: you against the computer. */
@Composable
fun ChineseCheckersRoute(
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
        onDifficultyChosen = viewModel::onDifficultyChosen
    )
}

@Composable
fun ChineseCheckersScreen(
    uiState: ChineseCheckersUiState,
    onBack: () -> Unit,
    onHoleTap: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
    onDifficultyChosen: (ChineseCheckersDifficulty) -> Unit = {}
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
            val humanWon = uiState.winner == CHINESE_CHECKERS_HUMAN
            val inset = Modifier.padding(horizontal = 16.dp - BoardSideMargin)
            when {
                humanWon -> StatusLine("All 10 pegs home - you win! Claiming your coupon…", peg = null, modifier = inset, trophy = true)
                uiState.winner != null -> StatusLine("The computer filled its point - it wins", peg = CHINESE_CHECKERS_COMPUTER, modifier = inset)
                uiState.isComputerThinking || !uiState.isHumanTurn -> StatusLine("Computer is moving…", peg = CHINESE_CHECKERS_COMPUTER, modifier = inset, busy = true)
                uiState.selected != null && uiState.pendingMoves.isEmpty() -> StatusLine("That peg is boxed in - pick another", peg = CHINESE_CHECKERS_HUMAN, modifier = inset)
                uiState.selected != null -> StatusLine("Tap a gold spot to move there", peg = CHINESE_CHECKERS_HUMAN, modifier = inset)
                else -> StatusLine("Your move - tap one of your pegs", peg = CHINESE_CHECKERS_HUMAN, modifier = inset, busy = uiState.isResolving)
            }

            BoardSlot(modifier = Modifier.padding(top = 12.dp)) {
                StarBoard(
                    uiState = uiState,
                    onHoleTap = onHoleTap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(BOARD_ASPECT)
                )
                if (humanWon) {
                    CelebrationBurst(
                        colors = listOf(PwGoldDark, PwGoldLight, RubyMid, Color.White),
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            ProgressPanel(uiState = uiState, onDifficultyChosen = onDifficultyChosen, modifier = inset.padding(top = 12.dp))

            if (uiState.isGameOver && !humanWon) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, peg: Int?, modifier: Modifier = Modifier, trophy: Boolean = false, busy: Boolean = false) {
    val gold = MaterialTheme.colorScheme.primary
    GameStatusBanner(accent = if (peg == CHINESE_CHECKERS_COMPUTER) RubyMid else gold, modifier = modifier) {
        when {
            trophy -> Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = gold, modifier = Modifier.size(26.dp))
            peg != null -> PegIcon(player = peg, modifier = Modifier.size(24.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )
        if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

/**
 * Under the board: how many pegs each side has home, and how strongly the
 * computer plays - a choice until the first move, then shown locked, so the
 * panel (and the board above it) never changes size mid-game.
 */
@Composable
private fun ProgressPanel(
    uiState: ChineseCheckersUiState,
    onDifficultyChosen: (ChineseCheckersDifficulty) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(NavyPlateTop, NavyPlateBottom)))
            .border(1.dp, GoldRim, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PegsHome(label = "You", player = CHINESE_CHECKERS_HUMAN, count = uiState.pegsHome(CHINESE_CHECKERS_HUMAN), modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(12.dp))
            PegsHome(label = "Computer", player = CHINESE_CHECKERS_COMPUTER, count = uiState.pegsHome(CHINESE_CHECKERS_COMPUTER), modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Difficulty",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
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

/** "●  You  3/10": one side's pegs in its goal point. */
@Composable
private fun PegsHome(label: String, player: Int, count: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PegIcon(player = player, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f)
        )
        Text(
            text = "$count/${ChineseCheckersBoard.PEGS_PER_PLAYER} home",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (player == CHINESE_CHECKERS_HUMAN) PwGoldLight else MaterialTheme.colorScheme.onSurface
        )
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
            .background(if (chosen) Brush.linearGradient(listOf(PwGoldLight, PwGoldDark)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
            .border(1.dp, if (chosen) GoldRim else Brush.linearGradient(listOf(PwGoldDark.copy(alpha = 0.6f), PwGoldDark.copy(alpha = 0.6f))), shape)
            .selectable(selected = chosen, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun PegIcon(player: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawPeg(center = center, radius = size.minDimension * 0.45f, player = player)
    }
}

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
 * The star: a navy plate with a gold rim, each player's home point tinted in
 * their colour, a well for every hole and the pegs in them. The selected peg
 * glows, its destinations pulse gold, and the last move's route is traced
 * faintly while the peg hops along it.
 */
@Composable
private fun StarBoard(
    uiState: ChineseCheckersUiState,
    onHoleTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val holes = ChineseCheckersBoard.holes
    val currentOnTap by rememberUpdatedState(onHoleTap)
    val canPlay = uiState.canPlay

    // The last move, hop by hop: 0 at the start, one more per hole landed on.
    val hop = remember { Animatable(0f) }
    val shownMove = uiState.lastMove
    LaunchedEffect(uiState.moveCount) {
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
            .semantics { contentDescription = "Chinese checkers board" }
            .pointerInput(canPlay) {
                if (!canPlay) return@pointerInput
                detectTapGestures { tap ->
                    val unit = size.width / BOARD_WIDTH_UNITS
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    // The nearest hole, if the tap is within about half a spacing of it.
                    val nearest = holes.indices.minBy { (centre + holeOffset(holes[it]) * unit - tap).getDistanceSquared() }
                    if ((centre + holeOffset(holes[nearest]) * unit - tap).getDistance() < unit * 0.6f) currentOnTap(nearest)
                }
            }
    ) {
        val unit = size.width / BOARD_WIDTH_UNITS
        val centre = Offset(size.width / 2f, size.height / 2f)
        fun at(i: Int): Offset = centre + holeOffset(holes[i]) * unit

        drawStarPlate(centre, unit)
        for (player in 1..2) drawHomePoint(player, centre, unit)

        val animating = shownMove != null && hop.value < shownMove.path.size
        shownMove?.let { drawRoute(it, ::at, unit) }

        // Wells, then destinations, then pegs on top.
        holes.indices.forEach { drawWell(at(it), unit) }
        uiState.legalTargets.forEach { drawTarget(at(it), unit, pulse) }
        holes.indices.forEach { i ->
            val player = uiState.position.cells[i]
            if (player == 0 || (animating && i == shownMove!!.to)) return@forEach
            if (i == uiState.selected) drawSelection(at(i), unit)
            drawPeg(at(i), unit * PEG_RADIUS, player)
        }

        // The moving peg, hopping from hole to hole along its route.
        if (animating) {
            val move = shownMove!!
            val stops = listOf(move.from) + move.path
            val leg = floor(hop.value).toInt().coerceIn(0, move.path.size - 1)
            val t = hop.value - leg
            val position = at(stops[leg]) + (at(stops[leg + 1]) - at(stops[leg])) * t
            val lift = sin(PI.toFloat() * t)
            val player = uiState.position.cells[move.to]
            drawCircle(Color.Black.copy(alpha = 0.25f), radius = unit * PEG_RADIUS, center = position + Offset(0f, unit * (0.08f + 0.18f * lift)))
            drawPeg(position - Offset(0f, unit * 0.22f * lift), unit * PEG_RADIUS * (1f + 0.18f * lift), player)
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

/** [player]'s home point, tinted in their colour; the opposite point is where they're heading. */
private fun DrawScope.drawHomePoint(player: Int, centre: Offset, unit: Float) {
    // The three corner holes of the point, then pushed out round them.
    val corners = if (player == CHINESE_CHECKERS_HUMAN) listOf(Hole(-4, 8), Hole(-4, 5), Hole(-1, 5)) else listOf(Hole(4, -8), Hole(4, -5), Hole(1, -5))
    val points = corners.map { centre + holeOffset(it) * unit }
    val middle = Offset(points.sumOf { it.x.toDouble() }.toFloat() / 3f, points.sumOf { it.y.toDouble() }.toFloat() / 3f)
    val path = Path().apply {
        points.forEachIndexed { i, p ->
            val q = middle + (p - middle) * 1.42f
            if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
        }
        close()
    }
    val colour = if (player == CHINESE_CHECKERS_HUMAN) PwGoldDark else RubyMid
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

/** A polished peg: gold for you, ruby for the computer, lit from the top left. */
private fun DrawScope.drawPeg(center: Offset, radius: Float, player: Int) {
    val (light, mid, dark) = if (player == CHINESE_CHECKERS_HUMAN) Triple(GoldPegLight, PwGoldDark, GoldPegDark) else Triple(RubyLight, RubyMid, RubyDark)
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
