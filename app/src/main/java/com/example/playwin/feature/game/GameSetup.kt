package com.example.playwin.feature.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.designsystem.theme.PwTableSurface
import com.example.playwin.core.designsystem.theme.PwTableSurfaceRaised

/*
 * The pre-game setup every vs-Computer game shares: a gold-rimmed panel of
 * pickers ("Play as", who goes first...), each option a gold-rimmed pill
 * filled gold when chosen. Chess shows its pickers under the board; the
 * other games float the panel over their board until the game is started.
 */

/** The ink on a chosen (gold) pill. */
val SetupInk = Color(0xFF0A1530)

/** How many options sit side by side in a pre-game picker. */
const val SETUP_COLUMNS = 3

/** The panel the pickers sit in: raised table surface, gold hairline rim. */
@Composable
fun GameSetupPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(PwTableSurfaceRaised, PwTableSurface)))
            .border(1.dp, Brush.verticalGradient(listOf(PwGoldDark.copy(alpha = 0.55f), PwGoldLight.copy(alpha = 0.15f))), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        content = content
    )
}

/**
 * Before the game starts: the board under a dim veil, with a [GameSetupPanel]
 * of [pickers] centred on it and a Start button below them. Taps never reach
 * the board underneath.
 */
@Composable
fun BoxScope.GameSetupOverlay(onStart: () -> Unit, pickers: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        GameSetupPanel(modifier = Modifier.padding(16.dp).widthIn(max = 420.dp)) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = pickers
            )
            GameGoldButton(
                text = "Start game",
                icon = Icons.Filled.PlayArrow,
                onClick = onStart,
                modifier = Modifier.padding(top = 14.dp).fillMaxWidth()
            )
        }
    }
}

/** A picker's heading, in gold capitals. */
@Composable
fun SetupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
        color = PwGoldDark,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/** A quiet line under the pickers saying what the choices add up to: who plays what, who goes first. */
@Composable
fun SetupNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * A picker: its [title], then its options as an even grid of equal-width
 * pills, [columns] to a row, so no row runs ragged or spills out of the panel.
 * [swatch] gives an option a dot of its colour before its label.
 */
@Composable
fun <T> SetupPicker(
    title: String,
    options: List<T>,
    label: (T) -> String,
    selected: T,
    onChoose: (T) -> Unit,
    columns: Int = SETUP_COLUMNS,
    swatch: ((T) -> Color)? = null
) {
    Column {
        SetupLabel(title)
        SetupGrid(
            options = options,
            label = label,
            selected = selected,
            onChoose = onChoose,
            columns = columns,
            // A swatch already marks each pill; the gold fill marks the choice.
            ticks = swatch == null,
            leading = swatch?.let { colorOf -> { option -> { SetupSwatch(colorOf(option)) } } }
        )
    }
}

/**
 * Pre-game options as an even grid of equal-width pills, [columns] to a row.
 * [description] is what's read out for an option, when its label is too short to say it.
 */
@Composable
fun <T> SetupGrid(
    options: List<T>,
    label: (T) -> String,
    selected: T,
    onChoose: (T) -> Unit,
    ticks: Boolean = true,
    columns: Int = SETUP_COLUMNS,
    description: (T) -> String? = { null },
    leading: ((T) -> @Composable () -> Unit)? = null
) {
    // Many to a row need tighter gaps to leave the pills room for their labels.
    val gap = if (columns > SETUP_COLUMNS) 6.dp else 8.dp
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { option ->
                    SetupChip(
                        label = label(option),
                        chosen = option == selected,
                        onClick = { onChoose(option) },
                        modifier = Modifier.weight(1f),
                        tick = ticks,
                        description = description(option),
                        leading = leading?.invoke(option)
                    )
                }
                // Keeps a short last row's pills the same width as the rest.
                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One option in a pre-game picker: a gold-rimmed pill, filled gold with a tick
 * when [chosen]. A [leading] mark sits before the label; with [showLabel] false
 * it stands alone, [label] read out in its place.
 */
@Composable
fun SetupChip(
    label: String,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** False leaves out the tick, for pills too narrow to hold it; the gold fill still marks the choice. */
    tick: Boolean = true,
    /** What's read out in place of a [label] too short to say it. */
    description: String? = null,
    leading: (@Composable () -> Unit)? = null,
    showLabel: Boolean = true
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .shadow(elevation = if (chosen) 6.dp else 0.dp, shape = shape, ambientColor = PwGoldDark, spotColor = PwGoldDark)
            .clip(shape)
            .background(
                if (chosen) Brush.verticalGradient(listOf(Color(0xFFFFE9A8), PwGoldDark, PwGoldLight))
                else Brush.verticalGradient(listOf(Color(0xFF0E1A33), Color(0xFF0A1428)))
            )
            .border(1.dp, if (chosen) Brush.verticalGradient(listOf(Color(0xFFFFF4CF), Color(0xFF7A5512))) else Brush.verticalGradient(listOf(PwGoldDark.copy(alpha = 0.6f), PwGoldLight.copy(alpha = 0.25f))), shape)
            .selectable(selected = chosen, role = Role.RadioButton, onClick = onClick)
            .then(
                when {
                    description != null -> Modifier.semantics { contentDescription = description }
                    !showLabel -> Modifier.semantics { contentDescription = label }
                    else -> Modifier
                }
            )
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            // A Box, so a mark drawn in layers stacks rather than sits side by side.
            Box(contentAlignment = Alignment.Center, modifier = if (showLabel) Modifier.padding(end = 6.dp) else Modifier) {
                leading()
            }
            if (!showLabel) return@Row
        }
        if (chosen && tick) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = SetupInk,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(16.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium),
            color = if (chosen) SetupInk else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A glossy dot of [color], for a colour option's pill. */
@Composable
fun SetupSwatch(color: Color, size: Dp = 16.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(Color.Black.copy(alpha = 0.35f), r, center + Offset(0f, r * 0.12f))
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), color, color), center = center - Offset(r * 0.35f, r * 0.35f), radius = r * 1.3f),
            r * 0.92f
        )
        drawCircle(Color.Black.copy(alpha = 0.3f), r * 0.92f, style = Stroke(width = r * 0.12f))
    }
}

/** Who acts first: you, or the computer. */
enum class FirstTurn(val label: String) { YOU("You"), COMPUTER("Computer") }
