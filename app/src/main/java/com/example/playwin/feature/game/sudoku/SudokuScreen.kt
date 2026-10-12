package com.example.playwin.feature.game.sudoku

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.domain.sudoku.SUDOKU_BOX
import com.example.playwin.core.domain.sudoku.SUDOKU_SIZE
import com.example.playwin.core.domain.sudoku.SudokuDifficulty
import com.example.playwin.core.domain.sudoku.sudokuPeers
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameScaffold
import com.example.playwin.feature.game.GameStatusBanner

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val status = when {
                uiState.isGenerating -> "Dealing a new puzzle…"
                uiState.hasWon -> "Solved! Claiming your coupon…"
                uiState.isResolving -> "Checking your grid…"
                uiState.isFilledButWrong -> "Not quite - fix the cells in red"
                else -> "Fill every row, column and box with 1-9"
            }
            GameStatusBanner(accent = MaterialTheme.colorScheme.primary) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            DifficultyPicker(
                selected = uiState.difficulty,
                onSelect = onNewGame,
                modifier = Modifier.padding(top = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatChip(label = "Time", value = formatElapsed(uiState.elapsedSeconds), modifier = Modifier.weight(1f))
                StatChip(label = "Mistakes", value = uiState.mistakes.toString(), modifier = Modifier.weight(1f))
            }

            BoardSlot(modifier = Modifier.padding(vertical = 14.dp)) {
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

            NumberPad(uiState = uiState, onDigit = onDigit)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ControlButton(
                    label = if (uiState.notesMode) "Notes on" else "Notes",
                    icon = Icons.Filled.Edit,
                    active = uiState.notesMode,
                    enabled = uiState.canEdit,
                    onClick = onToggleNotes
                )
                ControlButton(
                    label = "Erase",
                    icon = Icons.AutoMirrored.Filled.Backspace,
                    enabled = uiState.canEdit,
                    onClick = onErase
                )
                ControlButton(
                    label = "New game",
                    icon = Icons.Filled.Refresh,
                    enabled = !uiState.isGenerating && !uiState.hasWon,
                    onClick = { onNewGame(uiState.difficulty) }
                )
            }
        }
    }
}

private val NavyTop = Color(0xFF16264A)
private val NavyBottom = Color(0xFF0A1428)
private val NavyCell = Color(0xFF1A2B50)
private val NavyCellAlt = Color(0xFF15233F)
private val Cream = Color(0xFFF4EBD3)
private val Ruby = Color(0xFFE0485A)
private val GoldRim = Brush.linearGradient(listOf(PwGoldDark, PwGoldLight, PwGoldDark))

/** The 9x9 grid: cream givens, gold entries, red clashes; the selected cell and its row, column and box are lit. */
@Composable
private fun SudokuBoard(uiState: SudokuUiState, onSelectCell: (Int) -> Unit) {
    val frameShape = RoundedCornerShape(14.dp)
    val conflicts = uiState.conflicts
    val selected = uiState.selected
    val selectedDigit = selected?.let { uiState.grid[it] }?.takeIf { it != 0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(elevation = 12.dp, shape = frameShape)
            .clip(frameShape)
            .background(Brush.verticalGradient(listOf(NavyTop, NavyBottom)))
            .border(2.dp, GoldRim, frameShape)
            .padding(6.dp)
            .drawWithContent {
                drawContent()
                val cell = size.width / SUDOKU_SIZE
                for (i in 1 until SUDOKU_SIZE) {
                    val thick = i % SUDOKU_BOX == 0
                    val color = if (thick) PwGoldDark.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f)
                    val stroke = if (thick) 2.5.dp.toPx() else 1.dp.toPx()
                    drawLine(color, Offset(i * cell, 0f), Offset(i * cell, size.height), stroke)
                    drawLine(color, Offset(0f, i * cell), Offset(size.width, i * cell), stroke)
                }
            }
    ) {
        for (row in 0 until SUDOKU_SIZE) {
            Row(modifier = Modifier.weight(1f)) {
                for (col in 0 until SUDOKU_SIZE) {
                    val index = row * SUDOKU_SIZE + col
                    SudokuCell(
                        digit = uiState.grid[index],
                        notes = uiState.notes[index],
                        row = row,
                        col = col,
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

@Composable
private fun RowScope.SudokuCell(
    digit: Int,
    notes: Set<Int>,
    row: Int,
    col: Int,
    isGiven: Boolean,
    isSelected: Boolean,
    isPeer: Boolean,
    isSameDigit: Boolean,
    isConflict: Boolean,
    onClick: () -> Unit
) {
    val boxShade = ((row / SUDOKU_BOX) + (col / SUDOKU_BOX)) % 2 == 0
    val background = when {
        isSelected -> PwGoldDark.copy(alpha = 0.42f)
        isConflict -> Ruby.copy(alpha = 0.22f)
        isSameDigit -> PwGoldDark.copy(alpha = 0.22f)
        isPeer -> Color.White.copy(alpha = 0.07f)
        boxShade -> NavyCell
        else -> NavyCellAlt
    }
    val description = buildString {
        append("Row ${row + 1}, column ${col + 1}, ")
        append(if (digit == 0) "empty" else digit.toString())
        if (isGiven) append(", given")
        if (isConflict) append(", clashes")
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .background(background)
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
                fontSize = 22.sp,
                fontWeight = if (isGiven) FontWeight.Bold else FontWeight.SemiBold,
                color = when {
                    isConflict -> Ruby
                    isGiven -> Cream
                    else -> PwGoldDark
                }
            )
        } else if (notes.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(2.dp)) {
                for (r in 0 until 3) {
                    Row(modifier = Modifier.weight(1f)) {
                        for (c in 0 until 3) {
                            val n = r * 3 + c + 1
                            Text(
                                text = if (n in notes) n.toString() else "",
                                fontSize = 9.sp,
                                lineHeight = 9.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Digits 1-9; each key shows how many of its digit are still to place, and dims once all nine are down. */
@Composable
private fun NumberPad(uiState: SudokuUiState, onDigit: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (digit in 1..SUDOKU_SIZE) {
            val remaining = uiState.remaining(digit)
            val enabled = uiState.canEdit && uiState.selected != null && (remaining > 0 || uiState.notesMode)
            val shape = RoundedCornerShape(10.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .shadow(elevation = 3.dp, shape = shape)
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(NavyTop, NavyBottom)))
                    .border(1.dp, if (enabled) GoldRim else Brush.linearGradient(listOf(NavyCell, NavyCell)), shape)
                    .clickable(enabled = enabled) { onDigit(digit) }
                    .clearAndSetSemantics {
                        contentDescription = "Enter $digit"
                        role = Role.Button
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = digit.toString(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Cream else Cream.copy(alpha = 0.3f)
                )
                Text(
                    text = if (uiState.isGenerating) "" else remaining.toString(),
                    fontSize = 10.sp,
                    color = if (enabled) PwGoldDark else PwGoldDark.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun DifficultyPicker(
    selected: SudokuDifficulty,
    onSelect: (SudokuDifficulty) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NavyBottom)
            .border(1.dp, GoldRim, shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SudokuDifficulty.entries.forEach { difficulty ->
            val isSelected = difficulty == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (isSelected) PwGoldDark else Color.Transparent)
                    .clickable { onSelect(difficulty) }
                    .clearAndSetSemantics {
                        contentDescription = "${difficulty.label} puzzle"
                        this.selected = isSelected
                        role = Role.Tab
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = difficulty.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSelected) NavyBottom else Cream
                )
            }
        }
    }
}

@Composable
private fun RowScope.ControlButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false
) {
    val shape = RoundedCornerShape(14.dp)
    val content = if (active) NavyBottom else Cream
    Row(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clip(shape)
            .background(if (active) PwGoldDark else NavyBottom)
            .border(1.dp, GoldRim, shape)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = content.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

/** A navy plaque with a gold rim: a small gold label over a bold white value. */
@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .shadow(elevation = 4.dp, shape = shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(NavyTop, NavyBottom)))
            .border(1.dp, GoldRim, shape)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp),
            color = PwGoldDark
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}

private fun formatElapsed(totalSeconds: Int): String = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
