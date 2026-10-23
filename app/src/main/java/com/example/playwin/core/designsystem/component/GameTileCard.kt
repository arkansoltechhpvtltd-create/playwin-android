package com.example.playwin.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playwin.core.designsystem.theme.LocalPwIsDarkTheme
import com.example.playwin.core.designsystem.theme.PlayWinTheme

/**
 * One cohesive premium tile shared by all 9 games: the same surface, gold
 * hairline frame and neutral icon badge in every case. The ONLY per-game
 * variation is the icon itself and its jewel accent colour - never the tile's
 * background - so the grid reads as a unified, classy set rather than a
 * patchwork of different pastel cards. A game with its own badge artwork passes
 * it as [image], which replaces the icon badge.
 */
@Composable
fun GameTileCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    image: Painter? = null
) {
    val isDark = LocalPwIsDarkTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
        label = "gameTileScale"
    )

    val borderAlpha = if (isDark) 0.32f else 0.55f

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .aspectRatio(0.95f)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = TILE_PADDING)
        ) {
            // Full artwork stands in for the icon badge; it's drawn a little larger
            // but takes the same total height, so labels stay level across the row.
            val badgeSize = if (image != null) BADGE_IMAGE_SIZE else ICON_BADGE_SIZE
            val gap = LABEL_GAP - (badgeSize - ICON_BADGE_SIZE)
            val density = LocalDensity.current
            val label = fitLabel(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                maxWidth = constraints.maxWidth,
                maxHeight = constraints.maxHeight - with(density) { (MIN_BADGE_SIZE + gap).roundToPx() }
            )
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                // The badge takes what the label leaves it, up to its full size.
                val badge = Modifier
                    .weight(1f, fill = false)
                    .sizeIn(maxWidth = badgeSize, maxHeight = badgeSize)
                    .aspectRatio(1f)
                if (image != null) {
                    Image(
                        painter = image,
                        contentDescription = null,
                        modifier = badge.clip(RoundedCornerShape(BADGE_IMAGE_CORNER))
                    )
                } else {
                    Box(
                        modifier = badge
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                            .border(1.dp, accentColor.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.fillMaxSize(0.5f)
                        )
                    }
                }
                Text(
                    text = title,
                    style = label.style,
                    textAlign = TextAlign.Center,
                    maxLines = LABEL_MAX_LINES,
                    modifier = Modifier
                        .padding(top = gap)
                        .fillMaxWidth()
                        .height(with(density) { label.height.toDp() })
                )
            }
        }
    }
}

/** A tile's label: its [style], and the height kept for it - two lines, whatever it needs. */
private class FittedLabel(val style: TextStyle, val height: Int)

/**
 * Sizes a game's name for its tile: at most two centred lines, broken only
 * between words, in [maxHeight] at most. The tile keeps its size whatever the
 * phone's font scale or density, so where two lines at [style]'s size won't fit
 * the label steps down until they do, and further for a word too wide for a
 * line. The height kept is two lines at the size every name gets, so badges and
 * labels line up from tile to tile whether a name takes one line or two.
 */
@Composable
private fun fitLabel(text: String, style: TextStyle, maxWidth: Int, maxHeight: Int): FittedLabel {
    val measurer = rememberTextMeasurer()
    return remember(text, style, maxWidth, maxHeight, measurer) {
        fun sized(size: TextUnit) =
            style.copy(fontSize = size, lineHeight = style.lineHeight * (size.value / style.fontSize.value))
        fun TextUnit.smaller() = (value - LABEL_SIZE_STEP).sp
        fun twoLines(size: TextUnit) = measurer.measure("A\nA", sized(size)).size.height

        var size = style.fontSize
        while (twoLines(size) > maxHeight && size > LABEL_SIZE_FLOOR) size = size.smaller()
        val height = twoLines(size)

        val words = text.split(' ')
        val width = Constraints(maxWidth = maxWidth)
        while (size > LABEL_SIZE_FLOOR) {
            val layout = measurer.measure(text, sized(size), maxLines = LABEL_MAX_LINES, constraints = width)
            val wordsFit = words.all { measurer.measure(it, sized(size), softWrap = false).size.width <= maxWidth }
            if (wordsFit && !layout.hasVisualOverflow) break
            size = size.smaller()
        }
        FittedLabel(sized(size), height)
    }
}

private const val LABEL_MAX_LINES = 2
private const val LABEL_SIZE_STEP = 0.5f

/** Only stops the search; real phones never shrink a name this far. */
private val LABEL_SIZE_FLOOR = 4.sp

/** The least the badge gives up to the label. */
private val MIN_BADGE_SIZE = 32.dp
private val TILE_PADDING = 12.dp

private val ICON_BADGE_SIZE = 52.dp
private val BADGE_IMAGE_SIZE = 60.dp
private val BADGE_IMAGE_CORNER = 12.dp
private val LABEL_GAP = 12.dp

@Preview
@Composable
private fun GameTileCardPreview() {
    PlayWinTheme {
        GameTileCard(
            title = "Chinese Checkers",
            icon = Icons.Filled.Hexagon,
            onClick = {}
        )
    }
}
