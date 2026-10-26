package com.example.playwin.feature.game.snakesandladders

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.designsystem.theme.PwTableNavyTop
import com.example.playwin.core.designsystem.theme.PwTableSurface
import com.example.playwin.core.designsystem.theme.PwTableSurfaceRaised
import com.example.playwin.core.designsystem.theme.PwTableTextMuted
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersBoard
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersMove
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersRules
import com.example.playwin.feature.game.CelebrationBurst
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// --- Geometry, in cell units (multiply by the cell size for pixels): the 10x10
// grid fills the top of the board, square 100 at the top left, and a strip
// below it holds the tokens that haven't reached square 1 yet. ---

private const val COLUMNS = SnakesAndLaddersBoard.COLUMNS
private const val START_STRIP = 0.9f
private const val BOARD_HEIGHT = COLUMNS + START_STRIP

/** Gold for the player it's drawn for - "you" on every screen - and pearl for the other side. */
val SlGoldToken = listOf(Color(0xFFFFE9A8), PwGoldDark, PwGoldLight)
val SlPearlToken = listOf(Color.White, Color(0xFFE3E7EF), Color(0xFF9AA3B5))

private val SNAKE_COLORS = listOf(
    Color(0xFF2FA58C) to Color(0xFF0F5346), // emerald
    Color(0xFFD0506A) to Color(0xFF6E1A2C), // crimson
    Color(0xFF9B7FD4) to Color(0xFF3E2A6B) // amethyst
)

/** The centre of [square] on the grid; [SnakesAndLaddersRules.START] is [player]'s place in the start strip. */
private fun squarePoint(square: Int, player: Int): Offset {
    if (square <= SnakesAndLaddersRules.START) return Offset(0.55f + player * 0.85f, COLUMNS + START_STRIP / 2f)
    val row = SnakesAndLaddersBoard.rowOf(square)
    val column = SnakesAndLaddersBoard.columnOf(square)
    return Offset(column + 0.5f, COLUMNS - 1 - row + 0.5f)
}

/**
 * A point along the snake from [head] (t = 0) to [tail] (t = 1): a gentle S-curve
 * either side of the straight line. Shared by the drawing and the slide down it.
 */
private fun snakePoint(head: Offset, tail: Offset, t: Float): Offset {
    val along = tail - head
    val length = along.getDistance()
    val normal = Offset(-along.y, along.x) / length
    val waves = max(1f, length / 2.2f)
    val sway = sin(t * waves * 2f * PI.toFloat()) * 0.42f * sin(t * PI.toFloat()).coerceAtLeast(0.35f)
    return head + along * t + normal * sway
}

private fun snakePath(head: Offset, tail: Offset, samples: Int = 28): List<Offset> =
    (0..samples).map { snakePoint(head, tail, it / samples.toFloat()) }

/**
 * Where one token is drawn right now. A roll walks it square by square, then a
 * ladder carries it straight up or a snake slides it down its own body. Read
 * only in the draw phase, so an animating token redraws only the token layer.
 */
private class TokenMotion(private val player: Int, square: Int) {
    private var square = square
    private var waypoints by mutableStateOf(listOf(squarePoint(square, player)))
    private var arc = 0f
    private val progress = Animatable(0f)

    private fun segment(): Pair<Int, Float> {
        val p = progress.value.coerceIn(0f, (waypoints.size - 1).toFloat())
        val i = p.toInt().coerceAtMost(waypoints.size - 2)
        return i to (p - i)
    }

    fun offset(): Offset {
        if (waypoints.size == 1) return waypoints[0]
        val (i, fraction) = segment()
        return lerp(waypoints[i], waypoints[i + 1], fraction)
    }

    /** How far off the board the token is mid-hop, in cell units. */
    fun lift(): Float = if (waypoints.size == 1) 0f else sin(segment().second * PI.toFloat()) * arc

    /** Moves to [target], playing [move] out step by step when it's the move that got there. */
    suspend fun moveTo(target: Int, move: SnakesAndLaddersMove?) {
        if (target == square) return
        val replay = move?.takeIf { it.player == player && it.from == square && it.to == target }
        square = target
        if (replay == null) {
            // Nothing to replay (a new game, or rejoining mid-game): glide straight there.
            animate(listOf(offset(), squarePoint(target, player)), 0f, tween(400, easing = FastOutSlowInEasing))
            return
        }
        delay(SnakesAndLaddersTiming.DICE_SETTLE_MILLIS)
        val steps = replay.landed - replay.from
        if (steps > 0) {
            val walk = listOf(offset()) + (replay.from + 1..replay.landed).map { squarePoint(it, player) }
            animate(walk, 0.32f, tween((SnakesAndLaddersTiming.STEP_MILLIS * steps).toInt(), easing = LinearEasing))
        }
        if (replay.to != replay.landed) {
            val head = squarePoint(replay.landed, player)
            val end = squarePoint(replay.to, player)
            val path = if (replay.hitSnake) snakePath(head, end) else listOf(head, end)
            animate(path, 0f, tween(SnakesAndLaddersTiming.JUMP_MILLIS.toInt(), easing = FastOutSlowInEasing))
        }
    }

    private suspend fun animate(points: List<Offset>, hopArc: Float, spec: TweenSpec<Float>) {
        progress.snapTo(0f)
        arc = hopArc
        waypoints = points
        progress.animateTo((points.size - 1).toFloat(), spec)
    }
}

@Composable
private fun rememberTurnPulse(): State<Float> {
    val transition = rememberInfiniteTransition(label = "slTurn")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "slTurnPulse"
    )
}

/**
 * The board: navy squares numbered 1..100 in boustrophedon order, gold
 * ladders, jewel-toned snakes, and one token per player in [tokenColors]
 * (a light-to-dark gradient each). [lastMove] is the roll that produced
 * [positions] - the token it moved walks it out, climbing or sliding at the
 * end; any other change just glides. [activePlayer]'s token breathes a gold
 * halo while it's their roll.
 */
@Composable
fun SnakesAndLaddersBoard(
    positions: List<Int>,
    lastMove: SnakesAndLaddersMove?,
    tokenColors: List<List<Color>>,
    description: String,
    modifier: Modifier = Modifier,
    activePlayer: Int? = null,
    celebrate: Boolean = false
) {
    val textMeasurer = rememberTextMeasurer()
    val frameShape = RoundedCornerShape(22.dp)
    val boardShape = RoundedCornerShape(18.dp)

    val motions = remember { HashMap<Int, TokenMotion>() }
    positions.forEachIndexed { player, square ->
        key(player) {
            // A board that first appears just after a roll (an online game that has just
            // finished, say) starts that token where the roll began, so the move still plays out.
            val motion = motions.getOrPut(player) {
                TokenMotion(player, lastMove?.takeIf { it.player == player && it.to == square }?.from ?: square)
            }
            LaunchedEffect(square) { motion.moveTo(square, lastMove) }
        }
    }
    val pulse = if (activePlayer != null) rememberTurnPulse() else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(COLUMNS / BOARD_HEIGHT)
            .shadow(elevation = 12.dp, shape = frameShape)
            .clip(frameShape)
            .background(Brush.linearGradient(listOf(PwGoldDark, PwGoldLight, Color(0xFF7A5A14), PwGoldLight)))
            .padding(4.dp)
            .clip(boardShape)
            .semantics { contentDescription = description }
    ) {
        // The static board sits in its own layer so animating tokens never re-record it.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
        ) {
            val cell = size.width / COLUMNS
            drawSquares(cell, textMeasurer)
            SnakesAndLaddersBoard.LADDERS.forEach { (foot, top) -> drawLadder(cell, foot, top) }
            SnakesAndLaddersBoard.SNAKES.entries.forEachIndexed { i, (head, tail) ->
                drawSnake(cell, head, tail, SNAKE_COLORS[i % SNAKE_COLORS.size])
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cell = size.width / COLUMNS
            val glow = pulse?.value
            // Tokens resting on the same square sit side by side.
            val shared = positions.groupBy { it }.filterValues { it.size > 1 }.keys
            positions.indices
                .mapNotNull { player -> motions[player]?.let { player to it } }
                .sortedBy { (_, motion) -> motion.lift() }
                .forEach { (player, motion) ->
                    val square = positions[player]
                    val nudge = if (square in shared && square != SnakesAndLaddersRules.START) {
                        Offset(if (player == 0) -0.2f else 0.2f, 0f)
                    } else {
                        Offset.Zero
                    }
                    drawToken(
                        center = (motion.offset() + nudge) * cell,
                        radius = cell * 0.3f,
                        colors = tokenColors[player],
                        lift = motion.lift() * cell,
                        glow = if (player == activePlayer) glow else null
                    )
                }
        }

        if (celebrate) {
            CelebrationBurst(
                colors = listOf(PwGoldDark, PwGoldLight, MaterialTheme.colorScheme.secondary, Color.White),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** The grid: alternating navy squares, gold hairlines, each square's number, and 100 crowned in gold. */
private fun DrawScope.drawSquares(cell: Float, textMeasurer: TextMeasurer) {
    drawRect(color = PwTableNavyTop)
    val numberStyle = TextStyle(fontSize = (cell * 0.24f).toSp(), fontWeight = FontWeight.SemiBold)
    for (row in 0 until COLUMNS) {
        for (column in 0 until COLUMNS) {
            val square = SnakesAndLaddersBoard.squareAt(row, column)
            val topLeft = Offset(column * cell, (COLUMNS - 1 - row) * cell)
            val finish = square == SnakesAndLaddersRules.FINISH
            val base = if ((row + column) % 2 == 0) PwTableSurface else PwTableSurfaceRaised
            drawRect(
                brush = if (finish) {
                    Brush.radialGradient(listOf(PwGoldDark, PwGoldLight), center = topLeft + Offset(cell / 2, cell / 2), radius = cell)
                } else {
                    Brush.verticalGradient(listOf(base, base.copy(alpha = 0.82f)), startY = topLeft.y, endY = topLeft.y + cell)
                },
                topLeft = topLeft,
                size = Size(cell, cell)
            )
            drawRect(
                color = PwGoldDark.copy(alpha = 0.16f),
                topLeft = topLeft,
                size = Size(cell, cell),
                style = Stroke(width = 1f)
            )
            if (finish) {
                // 100 is the goal: its number sits large in the middle, clear of the frame's rounded corner.
                val label = textMeasurer.measure(
                    "$square",
                    numberStyle.copy(fontSize = (cell * 0.34f).toSp(), fontWeight = FontWeight.Bold, color = Color(0xFF241C08))
                )
                drawText(label, topLeft = topLeft + Offset((cell - label.size.width) / 2f, (cell - label.size.height) / 2f))
            } else {
                val label = textMeasurer.measure("$square", numberStyle.copy(color = PwTableTextMuted.copy(alpha = 0.8f)))
                drawText(label, topLeft = topLeft + Offset(cell * 0.08f, cell * 0.04f))
            }
        }
    }

    // The start strip under square 1.
    val stripTop = COLUMNS * cell
    drawRect(
        brush = Brush.verticalGradient(listOf(PwTableNavyTop, PwTableSurface), startY = stripTop, endY = size.height),
        topLeft = Offset(0f, stripTop),
        size = Size(size.width, size.height - stripTop)
    )
    drawLine(PwGoldDark.copy(alpha = 0.5f), Offset(0f, stripTop), Offset(size.width, stripTop), strokeWidth = 2f)
    val start = textMeasurer.measure(
        "START",
        TextStyle(fontSize = (cell * 0.3f).toSp(), fontWeight = FontWeight.Bold, color = PwGoldDark.copy(alpha = 0.75f), letterSpacing = (cell * 0.04f).toSp())
    )
    drawText(start, topLeft = Offset(cell * 2.2f, stripTop + (size.height - stripTop - start.size.height) / 2f))
}

/** Two gold rails from [foot] to [top] with rungs between, over a soft shadow. */
private fun DrawScope.drawLadder(cell: Float, foot: Int, top: Int) {
    val a = squarePoint(foot, 0) * cell
    val b = squarePoint(top, 0) * cell
    val along = b - a
    val length = along.getDistance()
    val unit = along / length
    val normal = Offset(-unit.y, unit.x) * (cell * 0.17f)
    // The rails run a little past both centres, so the ends sit inside the squares.
    val start = a - unit * (cell * 0.12f)
    val end = b + unit * (cell * 0.12f)
    val shadow = Offset(cell * 0.05f, cell * 0.07f)
    val railWidth = cell * 0.075f
    val rungs = (length / (cell * 0.38f)).toInt().coerceAtLeast(2)

    fun rungs(offset: Offset, color: Color, width: Float) {
        for (i in 1..rungs) {
            val p = lerp(start, end, i / (rungs + 1f)) + offset
            drawLine(color, p - normal, p + normal, strokeWidth = width, cap = StrokeCap.Round)
        }
    }

    // Shadow first, then the wood-gold rungs, then the rails on top.
    rungs(shadow, Color.Black.copy(alpha = 0.3f), railWidth * 0.8f)
    for (side in listOf(normal, -normal)) {
        drawLine(Color.Black.copy(alpha = 0.35f), start + side + shadow, end + side + shadow, strokeWidth = railWidth, cap = StrokeCap.Round)
    }
    rungs(Offset.Zero, Color(0xFFB98C2E), railWidth * 0.8f)
    for (side in listOf(normal, -normal)) {
        drawLine(
            brush = Brush.linearGradient(listOf(PwGoldDark, Color(0xFFFFF1C2), PwGoldLight), start = start + side, end = end + side),
            start = start + side,
            end = end + side,
            strokeWidth = railWidth,
            cap = StrokeCap.Round
        )
    }
}

/**
 * A snake from [head] down to [tail]: a tapering banded body built from
 * overlapping discs along its S-curve, a lighter belly line, and a head with
 * eyes and a forked tongue.
 */
private fun DrawScope.drawSnake(cell: Float, head: Int, tail: Int, colors: Pair<Color, Color>) {
    val (light, dark) = colors
    val h = squarePoint(head, 0)
    val t = squarePoint(tail, 0)
    val length = (t - h).getDistance()
    val samples = (length * 14).toInt().coerceAtLeast(24)
    val points = (0..samples).map { snakePoint(h, t, it / samples.toFloat()) * cell }
    fun width(i: Int) = cell * (0.17f - 0.12f * i / samples)

    // Shadow, then the body from the tail up so the head is drawn on top.
    val shadow = Offset(cell * 0.05f, cell * 0.08f)
    for (i in samples downTo 0) drawCircle(Color.Black.copy(alpha = 0.18f), width(i), points[i] + shadow)
    for (i in samples downTo 0) {
        val band = (i / 4) % 2 == 0
        drawCircle(
            brush = Brush.radialGradient(
                listOf(if (band) light else light.copy(alpha = 0.85f), dark),
                center = points[i] - Offset(width(i) * 0.3f, width(i) * 0.3f),
                radius = width(i) * 1.4f
            ),
            radius = width(i),
            center = points[i]
        )
    }
    // A gold belly stripe down the middle of the body.
    val belly = Path().apply {
        moveTo(points[2].x, points[2].y)
        for (i in 3 until samples) lineTo(points[i].x, points[i].y)
    }
    drawPath(belly, PwGoldDark.copy(alpha = 0.35f), style = Stroke(width = cell * 0.025f, cap = StrokeCap.Round))

    // The head, pointing back along the body's first stretch.
    val neck = points[minOf(3, samples)]
    val headCenter = points[0]
    val angle = atan2(headCenter.y - neck.y, headCenter.x - neck.x)
    val forward = Offset(cos(angle), sin(angle))
    val side = Offset(-forward.y, forward.x)
    val headRadius = cell * 0.2f
    drawCircle(
        brush = Brush.radialGradient(listOf(light, dark), center = headCenter - Offset(headRadius * 0.3f, headRadius * 0.3f), radius = headRadius * 1.5f),
        radius = headRadius,
        center = headCenter + forward * (headRadius * 0.2f)
    )
    val tongueBase = headCenter + forward * (headRadius * 1.1f)
    val tongueTip = tongueBase + forward * (headRadius * 0.6f)
    val tongue = Color(0xFFE53950)
    drawLine(tongue, tongueBase, tongueTip, strokeWidth = cell * 0.025f, cap = StrokeCap.Round)
    drawLine(tongue, tongueTip, tongueTip + (forward + side) * (headRadius * 0.25f), strokeWidth = cell * 0.02f, cap = StrokeCap.Round)
    drawLine(tongue, tongueTip, tongueTip + (forward - side) * (headRadius * 0.25f), strokeWidth = cell * 0.02f, cap = StrokeCap.Round)
    for (s in listOf(1f, -1f)) {
        val eye = headCenter + forward * (headRadius * 0.45f) + side * (headRadius * 0.45f * s)
        drawCircle(Color(0xFFFFF1C2), headRadius * 0.24f, eye)
        drawCircle(Color(0xFF0B0B0B), headRadius * 0.12f, eye + forward * (headRadius * 0.05f))
    }
}

/**
 * A glossy playing piece: a shadow, the body in [colors] (light to dark), a dark
 * rim and a highlight. [lift] raises it off its shadow mid-hop; a non-null
 * [glow] (0..1) breathes a gold halo round the token whose roll it is.
 */
private fun DrawScope.drawToken(center: Offset, radius: Float, colors: List<Color>, lift: Float, glow: Float?) {
    val airborne = (lift / (radius * 3f)).coerceIn(0f, 1f)
    val r = radius * (1f + airborne * 0.15f)
    val body = center - Offset(0f, lift)
    drawCircle(Color.Black.copy(alpha = 0.4f * (1f - airborne * 0.5f)), r * (1f - airborne * 0.2f), center + Offset(0f, r * 0.25f))
    if (glow != null) {
        drawCircle(PwGoldDark.copy(alpha = 0.16f + 0.2f * glow), r * (1.45f + 0.25f * glow), body)
        drawCircle(PwGoldDark.copy(alpha = 0.7f + 0.3f * glow), r * 1.22f, body, style = Stroke(width = r * 0.12f))
    }
    drawCircle(
        brush = Brush.radialGradient(colors, center = body - Offset(r * 0.35f, r * 0.4f), radius = r * 1.6f),
        radius = r,
        center = body
    )
    drawCircle(Color.Black.copy(alpha = 0.45f), r, body, style = Stroke(width = r * 0.1f))
    drawCircle(colors.last().copy(alpha = 0.7f), r * 0.62f, body, style = Stroke(width = r * 0.08f))
    drawRoundRect(
        color = Color.White.copy(alpha = 0.55f),
        topLeft = body + Offset(-r * 0.5f, -r * 0.7f),
        size = Size(r * 0.55f, r * 0.3f),
        cornerRadius = CornerRadius(r * 0.15f)
    )
}

/** The line under the turn banner describing [move], from the point of view of [who] ("You", "Computer"…). */
fun moveNote(move: SnakesAndLaddersMove?, who: String, rollsAgain: Boolean): String? {
    move ?: return null
    return when {
        move.forfeited -> "$who rolled three 6s in a row - turn passed"
        move.climbedLadder -> "$who rolled ${move.roll} and climbed a ladder to ${move.to}" + if (rollsAgain) " - roll again!" else ""
        move.hitSnake -> "$who rolled ${move.roll} and slid down a snake to ${move.to}" + if (rollsAgain) " - roll again!" else ""
        rollsAgain -> "$who rolled a 6 - roll again!"
        else -> "$who rolled ${move.roll}"
    }
}
