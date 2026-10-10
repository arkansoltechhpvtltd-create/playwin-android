package com.example.playwin.feature.game

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.playwin.core.designsystem.theme.LocalPwIsDarkTheme
import com.example.playwin.core.designsystem.theme.PwGameTableTheme
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwTableNavyBottom
import com.example.playwin.core.designsystem.theme.PwTableNavyTop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// The small pieces of "chrome" every game screen shares around its board, so
// the turn line, the countdown, the win burst and how the board fits the
// screen are the same in each game.

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

/**
 * The board's place on a game screen: all the height left in the screen's
 * column once the status line above and the controls below are laid out, so
 * the board can never push them off screen. The board is laid out at the full
 * width it's given; if that would make it taller than the space left, it's
 * scaled down evenly - keeping its shape - until it fits, and centred. Taps
 * and drags still land where they're drawn.
 */
@Composable
fun ColumnScope.BoardSlot(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .scaleToFitHeight()
                .drawBehind {
                    // A soft gold glow the board sits in, so it reads as lit on the table rather than pasted on.
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(PwGoldDark.copy(alpha = 0.16f), Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.75f
                        ),
                        radius = size.maxDimension * 0.75f
                    )
                },
            content = content
        )
    }
}

private fun Modifier.scaleToFitHeight(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val scale = if (constraints.hasBoundedHeight && placeable.height > constraints.maxHeight) {
        constraints.maxHeight / placeable.height.toFloat()
    } else {
        1f
    }
    val width = (placeable.width * scale).roundToInt()
    val height = (placeable.height * scale).roundToInt()
    layout(width, height) {
        // Scaled about its centre, so it's centred in the shrunken footprint.
        placeable.placeWithLayer((width - placeable.width) / 2, (height - placeable.height) / 2) {
            scaleX = scale
            scaleY = scale
        }
    }
}

/**
 * Every game screen's frame: the deep navy table gradient fills the whole
 * window edge to edge - behind the status and navigation bars too - and the
 * screen is themed navy and gold ([PwGameTableTheme]) so its banners, panels
 * and buttons belong to the same scene. Content is padded clear of the bars
 * exactly as with a plain [Scaffold].
 */
@Composable
fun GameScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    LightSystemBarIcons()
    PwGameTableTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(PwTableNavyTop, PwTableNavyBottom)))
        ) {
            Scaffold(
                topBar = topBar,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content
            )
        }
    }
}

/** Light status/navigation bar icons while a game screen is up, since the table behind them is dark. */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    val appIsDark = LocalPwIsDarkTheme.current
    DisposableEffect(view, appIsDark) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = !appIsDark
            controller.isAppearanceLightNavigationBars = !appIsDark
        }
    }
}
