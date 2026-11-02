package com.example.playwin.feature.game.sudoku

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldInk
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.designsystem.theme.PwTableTextMuted
import com.example.playwin.core.domain.sudoku.SUDOKU_BOX
import com.example.playwin.core.domain.sudoku.SUDOKU_CELLS
import com.example.playwin.core.domain.sudoku.SUDOKU_SIZE
import com.example.playwin.core.domain.sudoku.SudokuDifficulty
import com.example.playwin.core.domain.sudoku.sudokuPeers
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameDock
import com.example.playwin.feature.game.GameDockButton
import com.example.playwin.feature.game.GameNote
import com.example.playwin.feature.game.GameScaffold
import com.example.playwin.feature.game.GameStatCard
import com.example.playwin.feature.game.GameTone

@Composable
fun SudokuRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: SudokuViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    SudokuScreen(
        uiState = uiState,
        onBack = onBack,
        onSelectCell = viewModel::onSelectCell,
        onDigit = viewModel::onDigit,
        onErase = viewModel::onErase,
        onToggleNotes = viewModel::onToggleNotes,
        onNewGame = viewModel::onNewGame
    )
}

@Composable
fun SudokuScreen(
    uiState: SudokuUiState,
    onBack: () -> Unit,
    onSelectCell: (Int) -> Unit,
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
    onToggleNotes: () -> Unit,
    onNewGame: (SudokuDifficulty) -> Unit,
    modifier: Modifier = Modifier
) {
    GameScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Sudoku", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val filled = if (uiState.isGenerating) 0 else uiState.grid.count { it != 0 }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameStatCard(label = "Time", value = formatElapsed(uiState.elapsedSeconds), icon = Icons.Filled.Timer, modifier = Modifier.weight(1f))
                GameStatCard(label = "Errors", value = uiState.mistakes.toString(), icon = Icons.Filled.ErrorOutline, modifier = Modifier.weight(1f))
                GameStatCard(
                    label = "Filled",
                    value = "$filled",
                    icon = Icons.Filled.GridOn,
                    highlight = uiState.hasWon,
                    progress = filled / SUDOKU_CELLS.toFloat(),
                    modifier = Modifier.weight(1f)
                )
            }

            DifficultyPicker(
                selected = uiState.difficulty,
                enabled = !uiState.hasWon,
                onSelect = onNewGame,
                modifier = Modifier.padding(top = 10.dp)
            )

            GameNote(
                text = when {
                    uiState.isGenerating -> "Dealing a new puzzle…"
                    uiState.hasWon -> "Solved! Checking for your coupon…"
                    uiState.isResolving -> "Checking your grid…"
                    uiState.isFilledButWrong -> "Not quite - fix the cells in red"
                    uiState.notesMode -> "Notes on - tap digits to pencil them in"
                    uiState.selected == null -> "Tap a cell, then pick a digit"
                    else -> "Fill every row, column and box with 1-9"
                },
                tone = when {
                    uiState.hasWon -> GameTone.WIN
                    uiState.isFilledButWrong -> GameTone.BAD
                    else -> GameTone.PLAIN
                }
            )

            BoardSlot(modifier = Modifier.padding(vertical = 10.dp)) {
                SudokuBoard(uiState = uiState, onSelectCell = onSelectCell)
                if (uiState.isGenerating) {
                    CircularProgressIndicator(color = PwGoldDark, modifier = Modifier.align(Alignment.Center))
                }
                if (uiState.hasWon) {
                    CelebrationBurst(
                        colors = listOf(PwGoldDark, PwGoldLight, MaterialTheme.colorScheme.secondary, Color.White),
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            GameDock {
                NumberPad(uiState = uiState, onDigit = onDigit)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameDockButton(
                        label = if (uiState.notesMode) "Notes on" else "Notes",
                        icon = Icons.Filled.Edit,
                        active = uiState.notesMode,
                        enabled = uiState.canEdit,
                        onClick = onToggleNotes,
                        modifier = Modifier.weight(1f)
                    )
                    GameDockButton(
                        label = "Erase",
                        icon = Icons.AutoMirrored.Filled.Backspace,
                        enabled = uiState.canEdit,
                        onClick = onErase,
                        modifier = Modifier.weight(1f)
                    )
                    GameDockButton(
                        label = "New",
                        icon = Icons.Filled.Refresh,
                        enabled = !uiState.isGenerating && !uiState.hasWon,
                        onClick = { onNewGame(uiState.difficulty) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private val Cream = Color(0xFFF4EBD3)
private val Ruby = Color(0xFFE0485A)
private val GoldBevel = Brush.linearGradient(listOf(Color(0xFFFFEBB0), PwGoldDark, PwGoldLight, Color(0xFF6E4B10), PwGoldDark))
private val BoxFace = Brush.verticalGradient(listOf(Color(0xFF1C3060), Color(0xFF14254A)))

/**
 * The 9x9 grid in a gold bevel: each 3x3 box is its own raised navy panel.
 * Cream givens, gold entries, red clashes; the selected cell glows gold and
 * its row, column and box are lit.
 */
@Composable
private fun SudokuBoard(uiState: SudokuUiState, onSelectCell: (Int) -> Unit) {
    val frameShape = RoundedCornerShape(22.dp)
    val wellShape = RoundedCornerShape(17.dp)
    val conflicts = uiState.conflicts
    val selected = uiState.selected
    val selectedDigit = selected?.let { uiState.grid[it] }?.takeIf { it != 0 }
    val boxGap = 4.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(elevation = 18.dp, shape = frameShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(frameShape)
            .background(GoldBevel)
            .padding(5.dp)
            .clip(wellShape)
            .background(Color(0xFF070F22))
            .drawBehind {
                val shade = 12.dp.toPx()
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), endY = shade))
            }
            .padding(6.dp)
    ) {
        // Every cell the same size, so digits can be sized to the cell rather than the font scale.
        val cell: Dp = (maxWidth - boxGap * (SUDOKU_BOX - 1)) / SUDOKU_SIZE
        Column(verticalArrangement = Arrangement.spacedBy(boxGap)) {
            for (boxRow in 0 until SUDOKU_BOX) {
                Row(horizontalArrangement = Arrangement.spacedBy(boxGap)) {
                    for (boxCol in 0 until SUDOKU_BOX) {
                        val boxShape = RoundedCornerShape(8.dp)
                        Column(
                            modifier = Modifier
                                .clip(boxShape)
                                .background(BoxFace)
                                .border(1.dp, Brush.verticalGradient(listOf(PwGoldDark.copy(alpha = 0.35f), PwGoldDark.copy(alpha = 0.08f))), boxShape)
                        ) {
                            for (r in 0 until SUDOKU_BOX) {
                                Row {
                                    for (c in 0 until SUDOKU_BOX) {
                                        val row = boxRow * SUDOKU_BOX + r
                                        val col = boxCol * SUDOKU_BOX + c
                                        val index = row * SUDOKU_SIZE + col
                                        SudokuCell(
                                            digit = uiState.grid[index],
                                            notes = uiState.notes[index],
                                            row = row,
                                            col = col,
                                            size = cell,
                                            // Thin rules inside the box, none along its edge.
                                            ruleRight = c < SUDOKU_BOX - 1,
                                            ruleBottom = r < SUDOKU_BOX - 1,
                                            isGiven = uiState.isGiven(index),
                                            isSelected = index == selected,
                                            isPeer = selected != null && sudokuPeers(index, selected),
                                            isSameDigit = selectedDigit != null && uiState.grid[index] == selectedDigit && index != selected,
                                            isConflict = index in conflicts,
                                            onClick = { onSelectCell(index) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SudokuCell(
    digit: Int,
    notes: Set<Int>,
    row: Int,
    col: Int,
    size: Dp,
    ruleRight: Boolean,
    ruleBottom: Boolean,
    isGiven: Boolean,
    isSelected: Boolean,
    isPeer: Boolean,
    isSameDigit: Boolean,
    isConflict: Boolean,
    onClick: () -> Unit
) {
    val wash by animateColorAsState(
        targetValue = when {
            isSelected -> Color(0xFF2A3F73)
            isConflict -> Ruby.copy(alpha = 0.24f)
            isSameDigit -> PwGoldDark.copy(alpha = 0.18f)
            isPeer -> Color.White.copy(alpha = 0.07f)
            else -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "sudokuCellWash"
    )
    val description = buildString {
        append("Row ${row + 1}, column ${col + 1}, ")
        append(if (digit == 0) "empty" else digit.toString())
        if (isGiven) append(", given")
        if (isConflict) append(", clashes")
    }
    val density = LocalDensity.current
    val digitSize = with(density) { (size * 0.56f).toSp() }
    val noteSize = with(density) { (size * 0.24f).toSp() }
    // A fresh entry drops in with a small spring.
    val pop = remember(digit) { Animatable(if (digit != 0 && !isGiven) 0.5f else 1f) }
    LaunchedEffect(digit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 600f)) }

    Box(
        modifier = Modifier
            .size(size)
            .drawBehind {
                drawRect(wash)
                val rule = Color.White.copy(alpha = 0.09f)
                val w = 1.dp.toPx()
                if (ruleRight) drawRect(rule, topLeft = Offset(this.size.width - w, 0f), size = Size(w, this.size.height))
                if (ruleBottom) drawRect(rule, topLeft = Offset(0f, this.size.height - w), size = Size(this.size.width, w))
                if (isSelected) {
                    // A warm glow from the middle of the chosen cell, inside a gold ring.
                    drawRect(
                        Brush.radialGradient(
                            listOf(PwGoldDark.copy(alpha = 0.6f), PwGoldDark.copy(alpha = 0.18f)),
                            center = center,
                            radius = this.size.minDimension * 0.7f
                        )
                    )
                    val inset = 1.5.dp.toPx()
                    drawRoundRect(
                        color = PwGoldDark,
                        topLeft = Offset(inset, inset),
                        size = Size(this.size.width - inset * 2, this.size.height - inset * 2),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                        style = Stroke(2.dp.toPx())
                    )
                }
            }
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                selected = isSelected
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        if (digit != 0) {
            Text(
                text = digit.toString(),
                fontSize = digitSize,
                fontWeight = if (isGiven) FontWeight.Bold else FontWeight.SemiBold,
                color = when {
                    isConflict -> Ruby
                    isGiven -> Cream
                    isSelected -> Color(0xFFFFE9A8)
                    else -> PwGoldDark
                },
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            )
        } else if (notes.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(2.dp)) {
                for (r in 0 until 3) {
                    Row(modifier = Modifier.weight(1f)) {
                        for (c in 0 until 3) {
                            val n = r * 3 + c + 1
                            Text(
                                text = if (n in notes) n.toString() else "",
                                fontSize = noteSize,
                                lineHeight = noteSize,
                                fontWeight = FontWeight.Medium,
                                color = PwTableTextMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Digits 1-9 as raised keys; a small gold badge says how many of each are
 * still to place, and a key shows a check once all nine are down.
 */
@Composable
private fun NumberPad(uiState: SudokuUiState, onDigit: (Int) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = 5.dp
        val key = (maxWidth - gap * (SUDOKU_SIZE - 1)) / SUDOKU_SIZE
        val density = LocalDensity.current
        val digitSize = with(density) { (key * 0.52f).toSp() }
        val badgeSize = with(density) { (key * 0.27f).toSp() }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            for (digit in 1..SUDOKU_SIZE) {
                val remaining = uiState.remaining(digit)
                val done = !uiState.isGenerating && remaining <= 0
                val enabled = uiState.canEdit && uiState.selected != null && (remaining > 0 || uiState.notesMode)
                val shape = RoundedCornerShape(10.dp)
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(width = key, height = key * 1.5f)
                        .shadow(elevation = if (enabled) 4.dp else 1.dp, shape = shape)
                        .clip(shape)
                        // A darker foot gives the key its thickness.
                        .background(Color(0xFF060C1A))
                        .padding(bottom = 3.dp)
                        .clip(shape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF243A6E), Color(0xFF132449))))
                        .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 0.5f to Color.Transparent))
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(PwGoldDark.copy(alpha = if (enabled) 0.85f else 0.3f), PwGoldLight.copy(alpha = if (enabled) 0.35f else 0.1f))),
                            shape
                        )
                        .clickable(interactionSource = interaction, indication = ripple(color = PwGoldDark), enabled = enabled) { onDigit(digit) }
                        .clearAndSetSemantics {
                            contentDescription = if (done) "$digit, all placed" else "Enter $digit, $remaining left"
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = digit.toString(),
                            fontSize = digitSize,
                            lineHeight = digitSize,
                            fontWeight = FontWeight.Bold,
                            color = Cream.copy(alpha = if (done) 0.3f else if (enabled) 1f else 0.75f),
                            maxLines = 1,
                            softWrap = false
                        )
                        Box(modifier = Modifier.padding(top = 2.dp).size(key * 0.34f), contentAlignment = Alignment.Center) {
                            if (done) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = PwGoldDark, modifier = Modifier.fillMaxSize())
                            } else if (!uiState.isGenerating) {
                                Text(
                                    text = remaining.toString(),
                                    fontSize = badgeSize,
                                    lineHeight = badgeSize,
                                    fontWeight = FontWeight.Bold,
                                    color = PwGoldDark.copy(alpha = if (enabled) 1f else 0.65f),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Easy / Medium / Hard as a gold-rimmed segmented control; the chosen one is polished gold. */
@Composable
private fun DifficultyPicker(
    selected: SudokuDifficulty,
    enabled: Boolean,
    onSelect: (SudokuDifficulty) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1428), Color(0xFF0E1A33))))
            .border(1.dp, Brush.verticalGradient(listOf(PwGoldDark.copy(alpha = 0.7f), PwGoldLight.copy(alpha = 0.25f))), shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SudokuDifficulty.entries.forEach { difficulty ->
            val isSelected = difficulty == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clip(shape)
                    .background(
                        if (isSelected) Brush.verticalGradient(listOf(Color(0xFFFFE9A8), PwGoldDark, PwGoldLight))
                        else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .clickable(enabled = enabled) { onSelect(difficulty) }
                    .clearAndSetSemantics {
                        contentDescription = "${difficulty.label} puzzle"
                        this.selected = isSelected
                        role = Role.Tab
                    }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = difficulty.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold, letterSpacing = 0.5.sp),
                    color = if (isSelected) PwGoldInk else Cream.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatElapsed(totalSeconds: Int): String = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
