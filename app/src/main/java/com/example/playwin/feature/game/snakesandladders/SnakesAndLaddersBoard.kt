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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldInk
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.designsystem.theme.PwMoonlight
import com.example.playwin.core.designsystem.theme.PwTableTextMuted
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersBoard
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersMove
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersRules
import com.example.playwin.feature.game.CelebrationBurst
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// --- Geometry, in cell units (see [onGrid] for pixels): the 10x10
// grid fills the top of the board, square 100 at the top left, and a strip
// below it holds the tokens that haven't reached square 1 yet. ---

private const val COLUMNS = SnakesAndLaddersBoard.COLUMNS
private const val START_STRIP = 0.9f
private const val BOARD_HEIGHT = COLUMNS + START_STRIP
/** How much taller than wide a square may grow to fill spare height. */
private const val MAX_CELL_STRETCH = 1.3f

/** Gold for the player it's drawn for - "you" on every screen - and pearl for the other side. Highlight, body, shade. */
val SlGoldToken = listOf(Color(0xFFFFF0BE), PwGoldDark, Color(0xFF8A6215))
val SlPearlToken = listOf(Color.White, Color(0xFFDDE3EE), Color(0xFF7D8799))

// The board's own palette: two navies a step apart for the squares, and the
// honey-gold wood the ladders are cut from.
private val CellDark = Color(0xFF0E1B38)
private val CellLight = Color(0xFF1C3263)
private val GridLine = PwGoldDark.copy(alpha = 0.38f)
private val WoodShade = Color(0xFF5A3A0E)
private val WoodBody = Color(0xFFB98530)
private val WoodLight = Color(0xFFEDC66A)
private val WoodShine = Color(0xFFFFF1C8)

/** A snake's skin: its belly-lit [light], its [body] colour, the [dark] it shades into, and the [pattern] diamonds down its back. */
private class SnakeSkin(val light: Color, val body: Color, val dark: Color, val pattern: Color)

/** One distinct skin per snake on the board, in the order [SnakesAndLaddersBoard.SNAKES] lists them. */
private val SNAKE_SKINS = listOf(
    SnakeSkin(Color(0xFF8BF0C6), Color(0xFF26A584), Color(0xFF0A4637), Color(0xFF05291F)), // emerald
    SnakeSkin(Color(0xFFFF9AA8), Color(0xFFD23C58), Color(0xFF5C0F20), Color(0xFF3A0613)), // crimson
    SnakeSkin(Color(0xFFDCC6FF), Color(0xFF8C69D8), Color(0xFF34205E), Color(0xFF1F1240)), // amethyst
    SnakeSkin(Color(0xFFFFDC8A), Color(0xFFE38A1C), Color(0xFF693805), Color(0xFF3F2002)), // amber
    SnakeSkin(Color(0xFFFFF6DE), Color(0xFFD8B987), Color(0xFF6B5230), Color(0xFF4A3618)), // sand python
    SnakeSkin(Color(0xFFFFB797), Color(0xFFE5593A), Color(0xFF66200E), Color(0xFF3D1106)), // coral
    SnakeSkin(Color(0xFFE3F59A), Color(0xFF8DBE2C), Color(0xFF33500C), Color(0xFF1E3005)), // lime
    SnakeSkin(Color(0xFFFFB3E6), Color(0xFFC93B98), Color(0xFF570F44), Color(0xFF36072A)), // orchid
    SnakeSkin(Color(0xFF9DF5F2), Color(0xFF1DA8B0), Color(0xFF09444A), Color(0xFF032A2E)) // teal
)

/** The centre of [square] on the grid; [SnakesAndLaddersRules.START] is [player]'s place in the start strip. */
private fun squarePoint(square: Int, player: Int): Offset {
    if (square <= SnakesAndLaddersRules.START) return Offset(0.6f + player * 0.85f, COLUMNS + START_STRIP / 2f)
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

/** A point in cell units to pixels, for squares [cell] wide and [cellH] tall. */
private fun Offset.onGrid(cell: Float, cellH: Float): Offset = Offset(x * cell, y * cellH)

private fun Offset.normalized(): Offset = getDistance().let { if (it == 0f) this else this / it }

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
 * The board: a bevelled gold frame round two-tone navy squares numbered
 * 1..100 in boustrophedon order, honey-gold ladders, jewel-skinned snakes, and
 * one glossy pawn per player in [tokenColors] (highlight, body, shade).
 * [lastMove] is the roll that produced [positions] - the token it moved walks
 * it out, climbing or sliding at the end; any other change just glides.
 * [activePlayer]'s pawn stands in a breathing gold ring while it's their roll.
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
    val frameShape = RoundedCornerShape(16.dp)
    val frameWidth = 7.dp

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

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Square cells across the full width; given height to spare, they grow up to
        // MAX_CELL_STRETCH times taller so the board fills a tall screen. Short on
        // height, the whole board shrinks instead, cells staying square.
        val widthCell = (maxWidth - frameWidth * 2) / COLUMNS
        val heightCell = if (constraints.hasBoundedHeight) (maxHeight - frameWidth * 2) / BOARD_HEIGHT else widthCell
        val cellWidth = minOf(widthCell, heightCell)
        val cellHeight = heightCell.coerceIn(cellWidth, cellWidth * MAX_CELL_STRETCH)
        Box(
            modifier = Modifier
                .size(cellWidth * COLUMNS + frameWidth * 2, cellHeight * BOARD_HEIGHT + frameWidth * 2)
                .shadow(elevation = 18.dp, shape = frameShape, ambientColor = Color.Black, spotColor = Color.Black)
                .drawBehind { drawFrame(frameWidth.toPx(), 16.dp.toPx(), 9.dp.toPx()) }
                .padding(frameWidth)
                .clip(RoundedCornerShape(9.dp))
                .semantics { contentDescription = description }
        ) {
            // The static board sits in its own layer so animating tokens never re-record it.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()
            ) {
                val cell = size.width / COLUMNS
                val cellH = size.height / BOARD_HEIGHT
                drawSquares(cell, cellH, textMeasurer)
                SnakesAndLaddersBoard.LADDERS.forEach { (foot, top) -> drawLadder(cell, cellH, foot, top) }
                SnakesAndLaddersBoard.SNAKES.entries.forEachIndexed { i, (head, tail) ->
                    drawSnake(cell, cellH, head, tail, SNAKE_SKINS[i % SNAKE_SKINS.size])
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = size.width / COLUMNS
                val cellH = size.height / BOARD_HEIGHT
                val glow = pulse?.value
                // Tokens resting on the same square sit side by side.
                val shared = positions.groupBy { it }.filterValues { it.size > 1 }.keys
                positions.indices
                    .mapNotNull { player -> motions[player]?.let { player to it } }
                    // Airborne pawns over grounded ones, then nearer (lower) pawns in front.
                    .sortedWith(compareBy({ it.second.lift() }, { it.second.offset().y }))
                    .forEach { (player, motion) ->
                        val square = positions[player]
                        val nudge = if (square in shared && square != SnakesAndLaddersRules.START) {
                            Offset(if (player == 0) -0.2f else 0.2f, 0.04f * (player * 2 - 1))
                        } else {
                            Offset.Zero
                        }
                        drawPawn(
                            center = (motion.offset() + nudge).onGrid(cell, cellH),
                            radius = cell * 0.27f,
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
}

/**
 * The bevelled metal frame: a brushed-gold band with a bright outer edge, a
 * dark groove where it meets the board, and a gold stud in each corner.
 */
private fun DrawScope.drawFrame(width: Float, corner: Float, innerCorner: Float) {
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color(0xFF8A6418), Color(0xFFF6DC8E), PwGoldLight, Color(0xFFFFE9A8), Color(0xFF7A5512), PwGoldDark),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        cornerRadius = CornerRadius(corner)
    )
    // A light catch along the outer edge and a dark groove at the inner one read as a bevel.
    drawRoundRect(
        color = Color(0xFFFFF4CF).copy(alpha = 0.55f),
        topLeft = Offset(1f, 1f),
        size = Size(size.width - 2f, size.height - 2f),
        cornerRadius = CornerRadius(corner - 1f),
        style = Stroke(width = 1.5f)
    )
    val inset = width - 2.5f
    drawRoundRect(
        color = Color(0xFF3E2B07).copy(alpha = 0.85f),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(corner * 0.6f),
        style = Stroke(width = 3f)
    )
    // Each stud sits midway across the band where it turns the corner (on the diagonal).
    val stud = width * 0.3f
    val diagonal = corner - (corner + innerCorner) / 2f * 0.7071f
    for (x in listOf(diagonal, size.width - diagonal)) {
        for (y in listOf(diagonal, size.height - diagonal)) {
            val c = Offset(x, y)
            drawCircle(Color(0xFF3E2B07).copy(alpha = 0.6f), stud * 1.15f, c + Offset(0f, 1f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4CF), PwGoldDark, PwGoldLight), center = c - Offset(stud * 0.35f, stud * 0.35f), radius = stud * 1.4f), stud, c)
        }
    }
}

/**
 * The grid: two-tone navy squares with gold hairlines, each square's number,
 * square 1 in emerald with an arrow setting off, 100 crowned in gold, and the
 * start tray under it all.
 */
private fun DrawScope.drawSquares(cell: Float, cellH: Float, textMeasurer: TextMeasurer) {
    val gridBottom = COLUMNS * cellH
    val numberStyle = TextStyle(fontSize = (cell * 0.25f).toSp(), fontWeight = FontWeight.Bold, color = PwMoonlight.copy(alpha = 0.82f))
    for (row in 0 until COLUMNS) {
        for (column in 0 until COLUMNS) {
            val square = SnakesAndLaddersBoard.squareAt(row, column)
            val topLeft = Offset(column * cell, (COLUMNS - 1 - row) * cellH)
            val middle = topLeft + Offset(cell / 2f, cellH / 2f)
            val base = if ((row + column) % 2 == 0) CellDark else CellLight
            val brush = when (square) {
                SnakesAndLaddersRules.FINISH -> Brush.radialGradient(
                    listOf(Color(0xFFFFF0BE), PwGoldDark, PwGoldLight),
                    center = middle - Offset(cell * 0.2f, cell * 0.25f),
                    radius = cell * 1.1f
                )
                1 -> Brush.radialGradient(listOf(Color(0xFF2E9C80), Color(0xFF0F5346)), center = middle, radius = cell * 0.8f)
                // Each square lit softly from its top-left, so the grid has a little depth.
                else -> Brush.radialGradient(
                    listOf(lerpColor(base, Color.White, 0.07f), base),
                    center = topLeft + Offset(cell * 0.3f, cell * 0.25f),
                    radius = cell * 0.95f
                )
            }
            drawRect(brush = brush, topLeft = topLeft, size = Size(cell, cellH))

            when (square) {
                SnakesAndLaddersRules.FINISH -> {
                    drawCrown(middle - Offset(0f, cell * 0.14f), cell * 0.5f)
                    val label = textMeasurer.measure(
                        "100",
                        numberStyle.copy(fontSize = (cell * 0.26f).toSp(), fontWeight = FontWeight.ExtraBold, color = PwGoldInk)
                    )
                    drawText(label, topLeft = Offset(middle.x - label.size.width / 2f, topLeft.y + cellH * 0.5f))
                }
                else -> {
                    if (square == 1) drawStartArrow(middle + Offset(cell * 0.06f, cell * 0.12f), cell * 0.36f)
                    val label = textMeasurer.measure("$square", numberStyle)
                    val at = topLeft + Offset(cell * 0.08f, cell * 0.03f)
                    drawText(label, color = Color.Black.copy(alpha = 0.5f), topLeft = at + Offset(1f, 1.5f))
                    drawText(label, topLeft = at)
                }
            }
        }
    }

    // Crisp gold hairlines between the squares.
    val hairline = max(1f, cell * 0.022f)
    for (i in 1 until COLUMNS) {
        drawLine(GridLine, Offset(i * cell, 0f), Offset(i * cell, gridBottom), strokeWidth = hairline)
        drawLine(GridLine, Offset(0f, i * cellH), Offset(size.width, i * cellH), strokeWidth = hairline)
    }
    // Square 100 gets a fine dark-gold border so it sits in the frame as a jewel.
    drawRect(PwGoldLight, topLeft = Offset(hairline, hairline), size = Size(cell - hairline * 2, cellH - hairline * 2), style = Stroke(width = hairline * 1.5f))

    // A soft vignette keeps the eye in the middle of the board.
    drawRect(
        brush = Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
            center = Offset(size.width / 2f, gridBottom / 2f),
            radius = size.width * 0.78f
        ),
        size = Size(size.width, gridBottom)
    )

    // The start tray under square 1: darker navy behind a gold rule, where unstarted pawns wait.
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF060E20), Color(0xFF0C1832)), startY = gridBottom, endY = size.height),
        topLeft = Offset(0f, gridBottom),
        size = Size(size.width, size.height - gridBottom)
    )
    drawLine(
        brush = Brush.horizontalGradient(listOf(PwGoldLight, PwGoldDark, PwGoldLight)),
        start = Offset(0f, gridBottom + hairline),
        end = Offset(size.width, gridBottom + hairline),
        strokeWidth = hairline * 2.2f
    )
    val trayMiddle = gridBottom + (size.height - gridBottom) / 2f
    val start = textMeasurer.measure(
        "START",
        TextStyle(fontSize = (cell * 0.3f).toSp(), fontWeight = FontWeight.ExtraBold, color = PwGoldDark, letterSpacing = (cell * 0.06f).toSp())
    )
    drawText(start, topLeft = Offset(cell * 2.3f, trayMiddle - start.size.height / 2f))
    val goal = textMeasurer.measure(
        "First to 100 wins",
        TextStyle(fontSize = (cell * 0.24f).toSp(), fontWeight = FontWeight.Medium, color = PwTableTextMuted.copy(alpha = 0.8f), textAlign = TextAlign.End)
    )
    drawText(goal, topLeft = Offset(size.width - cell * 0.3f - goal.size.width, trayMiddle - goal.size.height / 2f))
}

private fun lerpColor(a: Color, b: Color, f: Float) = Color(
    red = a.red + (b.red - a.red) * f,
    green = a.green + (b.green - a.green) * f,
    blue = a.blue + (b.blue - a.blue) * f,
    alpha = 1f
)

/** A three-pointed gold crown [width] wide, centred on [center], for the finish square. */
private fun DrawScope.drawCrown(center: Offset, width: Float) {
    val w = width / 2f
    val h = width * 0.38f
    val crown = Path().apply {
        moveTo(center.x - w, center.y + h)
        lineTo(center.x - w * 1.02f, center.y - h * 0.55f)
        lineTo(center.x - w * 0.45f, center.y + h * 0.05f)
        lineTo(center.x, center.y - h * 1.05f)
        lineTo(center.x + w * 0.45f, center.y + h * 0.05f)
        lineTo(center.x + w * 1.02f, center.y - h * 0.55f)
        lineTo(center.x + w, center.y + h)
        close()
    }
    drawPath(crown, Color.Black.copy(alpha = 0.25f), style = Stroke(width = width * 0.1f, join = StrokeJoin.Round))
    drawPath(crown, Brush.verticalGradient(listOf(Color(0xFF7A5512), PwGoldInk), startY = center.y - h, endY = center.y + h))
    drawPath(crown, Color(0xFFFFF0BE).copy(alpha = 0.7f), style = Stroke(width = width * 0.035f, join = StrokeJoin.Round))
    for (tip in listOf(Offset(-w * 1.02f, -h * 0.55f), Offset(0f, -h * 1.05f), Offset(w * 1.02f, -h * 0.55f))) {
        drawCircle(Color(0xFFFFF0BE), width * 0.07f, center + tip)
    }
    drawCircle(Color(0xFFE5394F), width * 0.075f, center + Offset(0f, h * 0.45f))
}

/** A gold chevron on square 1, pointing the way round the board. */
private fun DrawScope.drawStartArrow(center: Offset, size: Float) {
    val arrow = Path().apply {
        moveTo(center.x - size * 0.35f, center.y - size * 0.4f)
        lineTo(center.x + size * 0.25f, center.y)
        lineTo(center.x - size * 0.35f, center.y + size * 0.4f)
    }
    drawPath(arrow, Color.Black.copy(alpha = 0.3f), style = Stroke(width = size * 0.26f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(arrow, PwGoldDark, style = Stroke(width = size * 0.18f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * A honey-gold wooden ladder from [foot] up to [top]: two rounded rails and
 * rungs between them, each shaded across its thickness so it reads as a solid
 * piece, pinned with a dark peg where rung meets rail, over a soft drop shadow.
 */
private fun DrawScope.drawLadder(cell: Float, cellH: Float, foot: Int, top: Int) {
    val a = squarePoint(foot, 0).onGrid(cell, cellH)
    val b = squarePoint(top, 0).onGrid(cell, cellH)
    val unit = (b - a).normalized()
    val normal = Offset(-unit.y, unit.x)
    val halfSpan = cell * 0.19f
    // The rails run a little past both centres, so the ends sit inside the squares.
    val start = a - unit * (cell * 0.2f)
    val end = b + unit * (cell * 0.2f)
    val length = (end - start).getDistance()
    val rail = cell * 0.085f
    val rungWidth = cell * 0.058f
    val shadow = Offset(cell * 0.06f, cell * 0.09f)
    val rungCount = (length / (cell * 0.33f)).toInt().coerceAtLeast(2)
    val rungAt = (1..rungCount).map { lerp(start, end, it / (rungCount + 1f)) }
    val sides = listOf(normal * halfSpan, normal * -halfSpan)

    // Shadow.
    val shade = Color.Black.copy(alpha = 0.38f)
    rungAt.forEach { p -> drawLine(shade, p - normal * halfSpan + shadow, p + normal * halfSpan + shadow, strokeWidth = rungWidth) }
    sides.forEach { s -> drawLine(shade, start + s + shadow, end + s + shadow, strokeWidth = rail, cap = StrokeCap.Round) }

    // Rungs, shaded across their thickness (along the ladder).
    rungAt.forEach { p ->
        drawLine(
            brush = Brush.linearGradient(
                listOf(WoodShade, WoodLight, WoodBody, WoodShade),
                start = p - unit * (rungWidth / 2f),
                end = p + unit * (rungWidth / 2f)
            ),
            start = p - normal * halfSpan,
            end = p + normal * halfSpan,
            strokeWidth = rungWidth
        )
    }
    // Rails, shaded across their width with a bright shine line, capped round.
    sides.forEach { s ->
        val railStart = start + s
        val railEnd = end + s
        drawLine(
            brush = Brush.linearGradient(
                0f to WoodShade, 0.25f to WoodBody, 0.42f to WoodShine, 0.6f to WoodLight, 1f to WoodShade,
                start = railStart - normal * (rail / 2f),
                end = railStart + normal * (rail / 2f)
            ),
            start = railStart,
            end = railEnd,
            strokeWidth = rail,
            cap = StrokeCap.Round
        )
        rungAt.forEach { p -> drawCircle(WoodShade, rail * 0.2f, p + s) }
    }
}

/**
 * A snake from [head] down to [tail], following [snakePoint]'s S-curve: a body
 * that swells from the neck and tapers to a pointed tail, shaded round with a
 * sheen on its lit side, diamonds down its back, and a head with slit-pupilled
 * eyes and a forked tongue sitting right on the head square.
 */
private fun DrawScope.drawSnake(cell: Float, cellH: Float, head: Int, tail: Int, skin: SnakeSkin) {
    val h = squarePoint(head, 0)
    val t = squarePoint(tail, 0)
    val length = (t - h).getDistance()
    val n = (length * 18).toInt().coerceAtLeast(40)
    val points = (0..n).map { snakePoint(h, t, it / n.toFloat()).onGrid(cell, cellH) }
    val tangents = points.indices.map { i -> (points[min(i + 1, n)] - points[max(i - 1, 0)]).normalized() }
    val normals = tangents.map { Offset(-it.y, it.x) }
    // The light comes from the top left; how much each slice of body faces it.
    val light = Offset(-0.6f, -0.8f)
    val facing = normals.map { it.x * light.x + it.y * light.y }
    val halfWidths = (0..n).map { i ->
        val f = i / n.toFloat()
        val neck = if (f < 0.06f) 0.72f + 0.28f * (f / 0.06f) else 1f
        cell * (0.12f * (1f - f).pow(0.65f) + 0.012f) * neck
    }

    /** The body's outline, [scale] of its width, slid [shift] widths towards the light. */
    fun outline(scale: Float, shift: Float): Path {
        val centre = points.indices.map { points[it] + normals[it] * (facing[it] * shift * halfWidths[it]) }
        return Path().apply {
            moveTo(centre[0].x + normals[0].x * halfWidths[0] * scale, centre[0].y + normals[0].y * halfWidths[0] * scale)
            for (i in 1..n) {
                val p = centre[i] + normals[i] * (halfWidths[i] * scale)
                lineTo(p.x, p.y)
            }
            val tip = points[n] + tangents[n] * (cell * 0.07f)
            lineTo(tip.x, tip.y)
            for (i in n downTo 0) {
                val p = centre[i] - normals[i] * (halfWidths[i] * scale)
                lineTo(p.x, p.y)
            }
            close()
        }
    }

    val body = outline(1f, 0f)
    translate(cell * 0.05f, cell * 0.08f) { drawPath(body, Color.Black.copy(alpha = 0.35f)) }
    drawPath(body, skin.dark)
    drawPath(outline(0.8f, 0.15f), skin.body)
    // Diamonds down the back, every so often along the body.
    val spacing = max(3, (cell * 0.2f / ((points[1] - points[0]).getDistance().coerceAtLeast(0.01f))).toInt())
    for (i in spacing until n - spacing / 2 step spacing) {
        val w = halfWidths[i]
        val p = points[i]
        val along = tangents[i] * (w * 0.95f)
        val across = normals[i] * (w * 0.6f)
        val diamond = Path().apply {
            moveTo(p.x - along.x, p.y - along.y)
            lineTo(p.x + across.x, p.y + across.y)
            lineTo(p.x + along.x, p.y + along.y)
            lineTo(p.x - across.x, p.y - across.y)
            close()
        }
        drawPath(diamond, skin.pattern.copy(alpha = 0.6f))
        drawCircle(skin.light.copy(alpha = 0.55f), w * 0.14f, p)
    }
    drawPath(outline(0.3f, 0.5f), skin.light.copy(alpha = 0.45f))
    drawPath(body, skin.pattern.copy(alpha = 0.7f), style = Stroke(width = cell * 0.014f, join = StrokeJoin.Round))

    drawSnakeHead(cell, points[0], tangents[0] * -1f, skin)
}

/** The head at [center], looking along [forward]: a rounded spade with eyes, nostrils and a forked tongue. */
private fun DrawScope.drawSnakeHead(cell: Float, center: Offset, forward: Offset, skin: SnakeSkin) {
    val degrees = atan2(forward.y, forward.x) * 180f / PI.toFloat()
    val length = cell * 0.44f
    val width = cell * 0.32f
    // Drawn pointing right (+x) and turned to face [forward].
    rotate(degrees, pivot = center) {
        val tongue = Color(0xFFE5394F)
        val tongueBase = center + Offset(length * 0.5f, 0f)
        val fork = tongueBase + Offset(cell * 0.13f, 0f)
        drawLine(tongue, tongueBase, fork, strokeWidth = cell * 0.025f, cap = StrokeCap.Round)
        drawLine(tongue, fork, fork + Offset(cell * 0.06f, -cell * 0.04f), strokeWidth = cell * 0.02f, cap = StrokeCap.Round)
        drawLine(tongue, fork, fork + Offset(cell * 0.06f, cell * 0.04f), strokeWidth = cell * 0.02f, cap = StrokeCap.Round)

        val topLeft = center - Offset(length * 0.42f, width / 2f)
        val shape = Path().apply {
            // Wide at the jaw, rounding to a blunt snout.
            moveTo(topLeft.x, center.y)
            cubicTo(topLeft.x, topLeft.y, topLeft.x + length * 0.55f, topLeft.y - width * 0.05f, topLeft.x + length, center.y)
            cubicTo(topLeft.x + length * 0.55f, topLeft.y + width * 1.05f, topLeft.x, topLeft.y + width, topLeft.x, center.y)
            close()
        }
        translate(cell * 0.05f, cell * 0.08f) { drawPath(shape, Color.Black.copy(alpha = 0.35f)) }
        drawPath(shape, Brush.radialGradient(listOf(skin.light, skin.body, skin.dark), center = center + Offset(length * 0.05f, -width * 0.2f), radius = length * 0.75f))
        drawPath(shape, skin.pattern.copy(alpha = 0.75f), style = Stroke(width = cell * 0.014f))

        for (side in listOf(-1f, 1f)) {
            val eye = center + Offset(length * 0.12f, side * width * 0.27f)
            drawCircle(skin.pattern, cell * 0.058f, eye)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3A6), Color(0xFFE8B826)), center = eye, radius = cell * 0.05f), cell * 0.046f, eye)
            drawOval(Color(0xFF0A0A0A), topLeft = eye - Offset(cell * 0.012f, cell * 0.035f), size = Size(cell * 0.024f, cell * 0.07f))
            drawCircle(Color.White.copy(alpha = 0.9f), cell * 0.012f, eye + Offset(-cell * 0.018f, -cell * 0.018f))
            drawCircle(skin.pattern, cell * 0.01f, center + Offset(length * 0.45f, side * width * 0.1f))
        }
    }
}

/**
 * A glossy playing pawn standing on [center]: a round base, a waisted body
 * and a ball head, each shaded from [colors] (highlight, body, shade) with a
 * specular glint. [lift] raises it off its shadow mid-hop; a non-null [glow]
 * (0..1) breathes a gold ring round its foot while it's that player's roll.
 */
fun DrawScope.drawPawn(center: Offset, radius: Float, colors: List<Color>, lift: Float, glow: Float?) {
    val (highlight, mid, shade) = colors
    val r = radius
    val airborne = (lift / (r * 3f)).coerceIn(0f, 1f)
    val ground = center + Offset(0f, r * 0.72f)
    val foot = ground - Offset(0f, lift)

    // Ground shadow (stays put, shrinking as the pawn rises), then the turn ring.
    val shadowW = r * 1.75f * (1f - airborne * 0.3f)
    drawOval(Color.Black.copy(alpha = 0.45f * (1f - airborne * 0.5f)), topLeft = ground - Offset(shadowW / 2f, r * 0.22f) + Offset(r * 0.08f, r * 0.1f), size = Size(shadowW, r * 0.55f))
    if (glow != null) {
        val ringW = r * (2.15f + 0.3f * glow)
        val ringH = ringW * 0.38f
        drawOval(PwGoldDark.copy(alpha = 0.18f + 0.18f * glow), topLeft = ground - Offset(ringW / 2f, ringH / 2f), size = Size(ringW, ringH))
        drawOval(PwGoldDark.copy(alpha = 0.65f + 0.35f * glow), topLeft = ground - Offset(ringW / 2f, ringH / 2f), size = Size(ringW, ringH), style = Stroke(width = r * 0.12f))
    }

    val across = Brush.horizontalGradient(
        0f to shade, 0.3f to mid, 0.42f to highlight, 0.62f to mid, 1f to shade,
        startX = foot.x - r * 0.8f,
        endX = foot.x + r * 0.8f
    )
    // Base: a thick disc - its dark side, then its top.
    val baseW = r * 1.55f
    drawOval(shade, topLeft = foot - Offset(baseW / 2f, r * 0.2f), size = Size(baseW, r * 0.52f))
    drawOval(across, topLeft = foot - Offset(baseW / 2f, r * 0.34f), size = Size(baseW, r * 0.5f))

    // Body: flaring from a narrow neck down to the base.
    val neckY = foot.y - r * 1.05f
    val bodyPath = Path().apply {
        moveTo(foot.x - r * 0.62f, foot.y - r * 0.12f)
        cubicTo(foot.x - r * 0.5f, foot.y - r * 0.45f, foot.x - r * 0.2f, foot.y - r * 0.6f, foot.x - r * 0.2f, neckY)
        lineTo(foot.x + r * 0.2f, neckY)
        cubicTo(foot.x + r * 0.2f, foot.y - r * 0.6f, foot.x + r * 0.5f, foot.y - r * 0.45f, foot.x + r * 0.62f, foot.y - r * 0.12f)
        quadraticTo(foot.x, foot.y + r * 0.12f, foot.x - r * 0.62f, foot.y - r * 0.12f)
        close()
    }
    drawPath(bodyPath, across)
    drawPath(bodyPath, shade.copy(alpha = 0.5f), style = Stroke(width = r * 0.05f))
    // Collar under the head.
    drawOval(across, topLeft = Offset(foot.x - r * 0.36f, neckY - r * 0.1f), size = Size(r * 0.72f, r * 0.22f))
    drawOval(shade.copy(alpha = 0.6f), topLeft = Offset(foot.x - r * 0.36f, neckY - r * 0.1f), size = Size(r * 0.72f, r * 0.22f), style = Stroke(width = r * 0.04f))

    // Head.
    val headCentre = Offset(foot.x, neckY - r * 0.42f)
    val headR = r * 0.5f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White, 0.18f to highlight, 0.6f to mid, 1f to shade,
            center = headCentre - Offset(headR * 0.35f, headR * 0.4f),
            radius = headR * 1.5f
        ),
        radius = headR,
        center = headCentre
    )
    drawCircle(shade.copy(alpha = 0.55f), headR, headCentre, style = Stroke(width = r * 0.05f))
    drawOval(
        Color.White.copy(alpha = 0.85f),
        topLeft = headCentre - Offset(headR * 0.55f, headR * 0.62f),
        size = Size(headR * 0.42f, headR * 0.28f)
    )
    // A long glint down the body's lit side.
    drawLine(
        Color.White.copy(alpha = 0.45f),
        Offset(foot.x - r * 0.12f, neckY + r * 0.15f),
        Offset(foot.x - r * 0.3f, foot.y - r * 0.3f),
        strokeWidth = r * 0.09f,
        cap = StrokeCap.Round
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
