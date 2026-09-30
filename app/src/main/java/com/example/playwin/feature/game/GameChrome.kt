package com.example.playwin.feature.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// The small pieces of "chrome" every game screen shares around its board, so
// the turn line, the countdown and the win burst look the same in each game.

/** A raised pill that carries the turn / result line, tinted from the left by [accent]. */
@Composable
fun GameStatusBanner(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.26f), Color.Transparent)))
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

/** The per-turn countdown as a draining ring; it turns to the error colour for the last few seconds. */
@Composable
fun TurnTimerRing(secondsLeft: Int, totalSeconds: Int, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        targetValue = secondsLeft / totalSeconds.toFloat(),
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "turnTimer"
    )
    val urgent = secondsLeft <= 5
    val ringColor = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outline
    Box(modifier = modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = inset, size = arcSize, style = Stroke(stroke))
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = inset,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "$secondsLeft",
            style = MaterialTheme.typography.labelLarge,
            color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
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
