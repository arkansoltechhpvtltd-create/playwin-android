package com.example.playwin.feature.game.snakesandladders

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.playwin.core.designsystem.theme.PwGoldDark
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** Shorter than [SnakesAndLaddersTiming.DICE_SETTLE_MILLIS], so the die has landed before the pawn sets off. */
private const val DIE_ROLL_MILLIS = 540

/** Pip positions per face, as fractions of the face's side. */
private val PIPS: Map<Int, List<Offset>> = run {
    val lo = 0.26f
    val mid = 0.5f
    val hi = 0.74f
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
 * A polished ivory die. Each new [value] throws it: it spins, bounces twice
 * with a little squash on each landing - its shadow tightening as it comes
 * down - and flickers through faces before settling on the roll. While
 * [enabled] it's the thing to tap, and a gold rim breathes round it.
 * Callers key it by the roll count so two equal rolls in a row still throw.
 */
@Composable
fun SnakesAndLaddersDie(
    value: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    var settledFace by remember { mutableIntStateOf(value ?: 6) }
    val roll = remember { Animatable(1f) }
    // Launched outside the effect so the throw always finishes once started.
    val scope = rememberCoroutineScope()
    LaunchedEffect(value) {
        if (value != null) {
            settledFace = value
            scope.launch {
                roll.snapTo(0f)
                roll.animateTo(1f, tween(DIE_ROLL_MILLIS, easing = LinearEasing))
            }
        }
    }
    val rim = if (enabled) {
        rememberInfiniteTransition(label = "slDieRim").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "slDieRimPulse"
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
                onClickLabel = "Roll the die",
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Height off the table, 0..1: two decaying bounces.
        fun height(t: Float) = abs(sin(t * 2.4f * PI.toFloat())) * (1f - t).pow(1.4f)

        // The shadow stays on the table.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hgt = height(roll.value)
            val w = this.size.width * 0.78f * (1f - hgt * 0.35f)
            drawOval(
                Color.Black.copy(alpha = 0.4f * (1f - hgt * 0.5f)),
                topLeft = Offset((this.size.width - w) / 2f, this.size.height * 0.82f),
                size = Size(w, this.size.height * 0.16f)
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = roll.value
                    val hgt = height(t)
                    // Squash on the landings, where the die is low but still moving.
                    val squash = if (t in 0.05f..0.95f && hgt < 0.08f) (0.08f - hgt) * 1.2f else 0f
                    rotationZ = (1f - t).pow(2) * 600f
                    translationY = -hgt * this.size.height * 0.55f
                    scaleX = 1f + hgt * 0.12f + squash
                    scaleY = 1f + hgt * 0.12f - squash
                    alpha = if (enabled || t < 1f) 1f else 0.85f
                }
        ) {
            val side = this.size.minDimension
            val inset = side * 0.1f
            val body = side - inset * 2
            val corner = CornerRadius(body * 0.24f)
            val topLeft = Offset(inset, inset)

            if (rim != null) {
                drawRoundRect(
                    color = PwGoldDark.copy(alpha = 0.18f + 0.22f * rim.value),
                    topLeft = topLeft - Offset(body * 0.1f, body * 0.1f),
                    size = Size(body * 1.2f, body * 1.2f),
                    cornerRadius = CornerRadius(body * 0.32f)
                )
            }
            // The cube's lower edge, then its face, then a bevel catching the light.
            drawRoundRect(Color(0xFFB9A57C), topLeft + Offset(0f, body * 0.06f), Size(body, body), corner)
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color.White, Color(0xFFF8F2E3), Color(0xFFE4D8BC)),
                    start = topLeft,
                    end = topLeft + Offset(body, body)
                ),
                topLeft = topLeft,
                size = Size(body, body),
                cornerRadius = corner
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color.White, Color.Transparent, Color(0xFFB9A57C).copy(alpha = 0.6f)),
                    start = topLeft,
                    end = topLeft + Offset(body, body)
                ),
                topLeft = topLeft + Offset(body * 0.03f, body * 0.03f),
                size = Size(body * 0.94f, body * 0.94f),
                cornerRadius = CornerRadius(body * 0.2f),
                style = Stroke(width = body * 0.04f)
            )
            if (rim != null) {
                drawRoundRect(
                    color = PwGoldDark.copy(alpha = 0.6f + 0.4f * rim.value),
                    topLeft = topLeft,
                    size = Size(body, body),
                    cornerRadius = corner,
                    style = Stroke(width = body * 0.05f)
                )
            }

            // Mid-throw the face flickers through other values before landing.
            val t = roll.value
            val face = if (t < 0.7f) ((t * 16).toInt() + settledFace) % 6 + 1 else settledFace
            // A single pip is the classic large crimson one.
            val single = face == 1
            val pipRadius = body * if (single) 0.13f else 0.085f
            val (pipLight, pipDark) = if (single) Color(0xFFE85A6E) to Color(0xFF8C1028) else Color(0xFF34405E) to Color(0xFF0B1226)
            PIPS.getValue(face).forEach { pip ->
                val c = topLeft + Offset(pip.x * body, pip.y * body)
                // A light lip under each pip makes it read as drilled in.
                drawCircle(Color.White.copy(alpha = 0.9f), pipRadius * 1.08f, c + Offset(pipRadius * 0.12f, pipRadius * 0.16f))
                drawCircle(
                    brush = Brush.radialGradient(listOf(pipDark, pipLight), center = c - Offset(pipRadius * 0.35f, pipRadius * 0.35f), radius = pipRadius * 1.6f),
                    radius = pipRadius,
                    center = c
                )
            }
        }
    }
}
