package com.example.playwin.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiPiece(
    val startX: Float,
    val fallDelay: Float,
    val drift: Float,
    val color: Color,
    val size: Float
)

private const val CONFETTI_START_DELAY_MILLIS = 250
private const val CONFETTI_DURATION_MILLIS = 3200

/**
 * A celebratory burst of confetti behind the win icon, plus a scale/rotate
 * entrance for the trophy itself. Starts just after the screen transition
 * settles and runs for a few seconds so it's actually seen, not missed while
 * the result screen is still animating in. Purely decorative - never blocks
 * interaction with the screen underneath.
 */
@Composable
fun WinCelebration(modifier: Modifier = Modifier) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )
    val pieces = remember {
        List(36) {
            ConfettiPiece(
                startX = Random.nextFloat(),
                fallDelay = Random.nextFloat() * 0.3f,
                drift = Random.nextFloat() * 2f - 1f,
                color = colors[it % colors.size],
                size = Random.nextFloat() * 4f + 4f
            )
        }
    }

    val progress = remember { Animatable(0f) }
    val trophyScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        trophyScale.animateTo(
            1f,
            tween(450, delayMillis = CONFETTI_START_DELAY_MILLIS, easing = LinearOutSlowInEasing)
        )
    }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            tween(CONFETTI_DURATION_MILLIS, delayMillis = CONFETTI_START_DELAY_MILLIS, easing = LinearOutSlowInEasing)
        )
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            val t = progress.value
            pieces.forEach { piece ->
                val local = ((t - piece.fallDelay) / (1f - piece.fallDelay)).coerceIn(0f, 1f)
                if (local <= 0f) return@forEach
                val y = local * size.height
                val x = piece.startX * size.width + sin(local * 6f + piece.drift) * 24f
                drawCircle(
                    color = piece.color.copy(alpha = (1f - local * 0.7f).coerceIn(0f, 1f)),
                    radius = piece.size,
                    center = Offset(x, y)
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
                    rotationZ = (1f - trophyScale.value) * -20f
                },
            tint = MaterialTheme.colorScheme.tertiary
        )
    }
}
