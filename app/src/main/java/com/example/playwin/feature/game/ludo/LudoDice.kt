package com.example.playwin.feature.game.ludo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.playwin.core.designsystem.theme.PwInkText
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val DICE_TUMBLE_MILLIS = 520

/** Pip positions per face, as fractions of the die's side. */
private val DICE_PIPS: Map<Int, List<Offset>> = run {
    val lo = 0.27f
    val mid = 0.5f
    val hi = 0.73f
    mapOf(
        1 to listOf(Offset(mid, mid)),
        2 to listOf(Offset(lo, lo), Offset(hi, hi)),
        3 to listOf(Offset(lo, lo), Offset(mid, mid), Offset(hi, hi)),
        4 to listOf(Offset(lo, lo), Offset(hi, lo), Offset(lo, hi), Offset(hi, hi)),
        5 to listOf(Offset(lo, lo), Offset(hi, lo), Offset(mid, mid), Offset(lo, hi), Offset(hi, hi)),
        6 to listOf(Offset(lo, lo), Offset(hi, lo), Offset(lo, mid), Offset(hi, mid), Offset(lo, hi), Offset(hi, hi))
    )
}

/**
 * An ivory die that tumbles - spinning, hopping and flickering through faces -
 * each time [value] changes to a new roll, then settles on it. It keeps
 * showing the last face after the roll is spent ([live] false), and pulses a
 * gold rim while it's [enabled] as the thing to tap.
 */
@Composable
fun LudoDice(
    value: Int?,
    live: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val gold = MaterialTheme.colorScheme.primary
    var settledFace by remember { mutableIntStateOf(value ?: 6) }
    val tumble = remember { Animatable(1f) }
    // Launched outside the effect so a roll that's cleared almost immediately
    // (a no-move pass) still finishes its tumble instead of freezing mid-spin.
    val scope = rememberCoroutineScope()
    LaunchedEffect(value) {
        if (value != null) {
            settledFace = value
            scope.launch {
                tumble.snapTo(0f)
                tumble.animateTo(1f, tween(DICE_TUMBLE_MILLIS, easing = LinearOutSlowInEasing))
            }
        }
    }

    val rimPulse = if (enabled) {
        val transition = rememberInfiniteTransition(label = "diceRim")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "diceRimPulse"
        )
    } else {
        null
    }

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Roll dice",
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = tumble.value
                    val hop = sin(t * PI.toFloat())
                    rotationZ = (1f - t) * -540f
                    scaleX = 1f + hop * 0.16f
                    scaleY = 1f + hop * 0.16f
                    translationY = -hop * this.size.height * 0.22f
                    alpha = if (live || enabled || t < 1f) 1f else 0.72f
                }
        ) {
            val side = this.size.minDimension
            val inset = side * 0.1f
            val body = side - inset * 2
            val corner = CornerRadius(body * 0.22f)
            val topLeft = Offset(inset, inset)

            // Soft drop shadow, then the darker "thickness" edge, then the face.
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.22f),
                topLeft = topLeft + Offset(0f, body * 0.09f),
                size = Size(body, body),
                cornerRadius = corner
            )
            drawRoundRect(
                color = Color(0xFFCFC3A6),
                topLeft = topLeft + Offset(0f, body * 0.04f),
                size = Size(body, body),
                cornerRadius = corner
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, Color(0xFFF4EEDF), Color(0xFFE6DCC4)),
                    start = topLeft,
                    end = topLeft + Offset(body, body)
                ),
                topLeft = topLeft,
                size = Size(body, body),
                cornerRadius = corner
            )
            if (rimPulse != null) {
                drawRoundRect(
                    color = gold.copy(alpha = 0.45f + 0.55f * rimPulse.value),
                    topLeft = topLeft,
                    size = Size(body, body),
                    cornerRadius = corner,
                    style = Stroke(width = body * (0.05f + 0.03f * rimPulse.value))
                )
            }

            // Mid-tumble the face flickers through other values before landing.
            val t = tumble.value
            val face = if (t < 0.75f) ((t * 14).toInt() + settledFace) % 6 + 1 else settledFace
            val pipRadius = body * 0.085f
            DICE_PIPS.getValue(face).forEach { pip ->
                val center = topLeft + Offset(pip.x * body, pip.y * body)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF4A4034), PwInkText),
                        center = center - Offset(pipRadius * 0.3f, pipRadius * 0.3f),
                        radius = pipRadius * 1.3f
                    ),
                    radius = pipRadius,
                    center = center
                )
            }
        }
    }
}

private class BurstPiece(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val size: Float,
    val color: Color
)

/**
 * A one-shot confetti burst from the centre of whatever it overlays: pieces fly
 * outward, tumble, sag under gravity and fade. Purely decorative.
 */
@Composable
fun CelebrationBurst(colors: List<Color>, modifier: Modifier = Modifier) {
    val pieces = remember(colors) {
        List(56) {
            BurstPiece(
                angle = Random.nextFloat() * 2f * PI.toFloat(),
                speed = Random.nextFloat() * 0.55f + 0.25f,
                spin = Random.nextFloat() * 720f - 360f,
                size = Random.nextFloat() * 0.012f + 0.012f,
                color = colors[it % colors.size]
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1800, easing = LinearEasing))
    }

    Canvas(modifier = modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        // Fast launch that eases out, plus a quadratic sag.
        val travel = 1f - (1f - t) * (1f - t)
        val fade = (1f - t).coerceIn(0f, 1f)
        val extent = size.minDimension
        pieces.forEach { piece ->
            val distance = travel * piece.speed * extent
            val center = Offset(
                x = size.width / 2f + cos(piece.angle) * distance,
                y = size.height / 2f + sin(piece.angle) * distance + t * t * extent * 0.28f
            )
            val w = piece.size * extent
            rotate(degrees = piece.spin * t, pivot = center) {
                drawRoundRect(
                    color = piece.color.copy(alpha = fade),
                    topLeft = center - Offset(w, w * 0.5f),
                    size = Size(w * 2f, w),
                    cornerRadius = CornerRadius(w * 0.25f)
                )
            }
        }
    }
}
