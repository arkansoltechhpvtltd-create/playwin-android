package com.example.playwin.feature.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playwin.core.designsystem.theme.PwEmeraldDark
import com.example.playwin.core.designsystem.theme.PwErrorDark
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldInk
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.designsystem.theme.PwTableSurface
import com.example.playwin.core.designsystem.theme.PwTableSurfaceRaised
import com.example.playwin.core.designsystem.theme.PwTableTextMuted

// The heads-up pieces every game screen is built from - stat cards over the
// board, one line of commentary, and a control dock under it - so each game's
// HUD and buttons look and behave the same.

/** How a [GameNote] is tinted: news in emerald, a setback in red, a win in gold. */
enum class GameTone { PLAIN, GOOD, BAD, WIN }

/** The one line of commentary between the HUD and the board. */
@Composable
fun GameNote(text: String, tone: GameTone, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        targetValue = when (tone) {
            GameTone.PLAIN -> PwTableTextMuted
            GameTone.GOOD -> PwEmeraldDark
            GameTone.BAD -> PwErrorDark
            GameTone.WIN -> PwGoldDark
        },
        label = "gameNoteColor"
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = if (tone == GameTone.PLAIN) FontWeight.Medium else FontWeight.SemiBold
        ),
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 22.dp)
            .padding(top = 8.dp)
    )
}

/**
 * A raised navy card with a gold rim: a small gold label over a bold value,
 * and optionally a thin gold [progress] bar along its foot. A [highlight]ed
 * card glows, as the card of whoever's turn it is does.
 */
@Composable
fun GameStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    highlight: Boolean = false,
    progress: Float? = null
) {
    val shape = RoundedCornerShape(18.dp)
    val glow by animateFloatAsState(if (highlight) 1f else 0f, tween(300), label = "statCardGlow")
    val bar by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), tween(500), label = "statCardProgress")
    Column(
        modifier = modifier
            .shadow(
                elevation = (4 + 8 * glow).dp,
                shape = shape,
                ambientColor = PwGoldDark.copy(alpha = glow),
                spotColor = PwGoldDark.copy(alpha = glow)
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(PwTableSurfaceRaised, PwTableSurface)))
            .background(Brush.horizontalGradient(listOf(PwGoldDark.copy(alpha = 0.14f * glow), Color.Transparent)))
            .border(
                width = (1f + 0.5f * glow).dp,
                brush = Brush.linearGradient(
                    listOf(PwGoldDark.copy(alpha = 0.45f + 0.55f * glow), PwGoldLight.copy(alpha = 0.2f + 0.6f * glow))
                ),
                shape = shape
            )
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = if (progress != null) 6.dp else 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = PwGoldDark, modifier = Modifier.padding(end = 4.dp).size(14.dp))
            }
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                color = PwGoldDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (progress != null) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bar)
                        .height(3.dp)
                        .background(Brush.horizontalGradient(listOf(PwGoldLight, PwGoldDark)))
                )
            }
        }
    }
}

/** The raised control dock under the board. */
@Composable
fun GameDock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(PwTableSurfaceRaised, PwTableSurface)))
            .border(1.dp, Brush.verticalGradient(listOf(PwGoldDark.copy(alpha = 0.55f), PwGoldLight.copy(alpha = 0.15f))), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** A gentle press-down as a control is held, so every button feels tactile. */
@Composable
private fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "pressScale"
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** The main call to action: a tall polished-gold pill with a light that sweeps across it now and then. */
@Composable
fun GameGoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(28.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val sweep by rememberInfiniteTransition(label = "goldSweep").animateFloat(
        initialValue = -0.6f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "goldSweepX"
    )
    Row(
        modifier = modifier
            .pressScale(interactionSource)
            .heightIn(min = 56.dp)
            .shadow(elevation = if (enabled) 10.dp else 0.dp, shape = shape, ambientColor = PwGoldDark, spotColor = PwGoldDark)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFFFFE9A8), PwGoldDark, PwGoldLight)))
            .drawWithContent {
                drawContent()
                if (enabled) {
                    val x = sweep * size.width
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                            start = Offset(x - size.height, 0f),
                            end = Offset(x, size.height)
                        )
                    )
                } else {
                    drawRect(Color(0xFF0A1428).copy(alpha = 0.55f))
                }
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color(0xFFFFF4CF), Color(0xFF7A5512))), shape)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = PwGoldInk, modifier = Modifier.padding(end = 8.dp).size(20.dp))
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp),
            color = PwGoldInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A secondary control: a navy pill with a gold rim, filled gold while [active]. */
@Composable
fun GameDockButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false
) {
    val shape = RoundedCornerShape(28.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val content = (if (active) PwGoldInk else MaterialTheme.colorScheme.onSurface).copy(alpha = if (enabled) 1f else 0.35f)
    Row(
        modifier = modifier
            .pressScale(interactionSource)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(
                if (active) {
                    Brush.verticalGradient(listOf(Color(0xFFFFE9A8), PwGoldDark, PwGoldLight))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF0E1A33), Color(0xFF0A1428)))
                }
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(PwGoldDark.copy(alpha = if (enabled) 0.9f else 0.3f), PwGoldLight.copy(alpha = if (enabled) 0.5f else 0.15f))),
                shape
            )
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (active) content else PwGoldDark.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
