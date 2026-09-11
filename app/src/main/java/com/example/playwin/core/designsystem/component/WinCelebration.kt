package com.example.playwin.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

private data class Sparkle(
    val x: Float,
    val y: Float,
    val phase: Float,
    val speed: Float,
    val size: Float
)

private const val CELEBRATION_START_DELAY_MILLIS = 250
private const val TROPHY_ENTRANCE_MILLIS = 500

/**
 * A classy, restrained celebration: a soft gold glow behind the trophy and a
 * scattering of twinkling gold sparkle marks - not falling confetti. Starts
 * just after the screen transition settles and keeps gently shimmering for
 * as long as the result screen is on view, so it's actually seen rather than
 * missed. Purely decorative - never blocks interaction underneath.
 */
@Composable
fun WinCelebration(modifier: Modifier = Modifier) {
    val gold = MaterialTheme.colorScheme.primary
    val emerald = MaterialTheme.colorScheme.secondary

    val sparkles = remember {
        List(20) {
            Sparkle(
                x = Random.nextFloat(),
                y = Random.nextFloat() * 0.75f + 0.05f,
                phase = Random.nextFloat(),
                speed = Random.nextFloat() * 0.6f + 0.7f,
                size = Random.nextFloat() * 3f + 3f
            )
        }
    }

    val trophyScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        trophyScale.animateTo(
            1f,
            tween(TROPHY_ENTRANCE_MILLIS, delayMillis = CELEBRATION_START_DELAY_MILLIS, easing = LinearOutSlowInEasing)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "winShimmer")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "sparkleTime"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = LinearOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            // Gentle gold glow behind the trophy - the only "large area" of
            // gold on screen, and even then at low, breathing opacity.
            drawCircle(
                color = gold.copy(alpha = 0.14f * glowPulse),
                radius = size.minDimension * 0.32f,
                center = Offset(size.width / 2f, size.height * 0.42f)
            )

            sparkles.forEach { sparkle ->
                val local = (time + sparkle.phase) % 1f
                val twinkle = (sin(local * 2 * Math.PI.toFloat()) * 0.5f + 0.5f)
                val drift = sin((time * sparkle.speed + sparkle.phase) * 2 * Math.PI.toFloat()) * 10f
                val cx = sparkle.x * size.width + drift
                val cy = sparkle.y * size.height - local * 18f
                val color = if (sparkle.phase < 0.75f) gold else emerald
                drawSparkle(
                    center = Offset(cx, cy),
                    size = sparkle.size,
                    color = color.copy(alpha = (0.25f + twinkle * 0.65f).coerceIn(0f, 1f))
                )
            }
        }

        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            modifier = Modifier
                .height(72.dp)
                .graphicsLayer {
                    scaleX = trophyScale.value
                    scaleY = trophyScale.value
                },
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/** A small four-point sparkle mark - the shimmer motif for gold accents. */
private fun DrawScope.drawSparkle(
    center: Offset,
    size: Float,
    color: Color
) {
    drawCircle(color = color.copy(alpha = color.alpha * 0.35f), radius = size * 1.6f, center = center)
    drawLine(
        color = color,
        start = Offset(center.x - size, center.y),
        end = Offset(center.x + size, center.y),
        strokeWidth = size * 0.35f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - size),
        end = Offset(center.x, center.y + size),
        strokeWidth = size * 0.35f,
        cap = StrokeCap.Round
    )
}
