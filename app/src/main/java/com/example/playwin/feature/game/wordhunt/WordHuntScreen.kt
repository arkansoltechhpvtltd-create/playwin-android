package com.example.playwin.feature.game.wordhunt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.wordhunt.PlacedWord
import com.example.playwin.core.domain.wordhunt.WORD_HUNT_TIME_WARNING_SECONDS
import com.example.playwin.core.domain.wordhunt.cells
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
import com.example.playwin.feature.game.wordhunt.online.WordHuntOnlineRoute

/** Entry point for Word Hunt: play the computer, or get paired with a random online player. */
@Composable
fun WordHuntRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<PlayMode?>(null) }
    when (mode) {
        null -> GameModeSelectScreen(title = "Word Hunt", onBack = onBack, onSelectMode = { mode = it })
        PlayMode.COMPUTER -> WordHuntComputerRoute(onBack = { mode = null }, onWin = onWin)
        PlayMode.ONLINE -> WordHuntOnlineRoute(
            onBack = { mode = null },
            onWin = onWin,
            onPlayComputer = { mode = PlayMode.COMPUTER }
        )
    }
}

@Composable
private fun WordHuntComputerRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: WordHuntViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    WordHuntScreen(
        uiState = uiState,
        onBack = onBack,
        onCellTap = viewModel::onCellTap,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordHuntScreen(
    uiState: WordHuntUiState,
    onBack: () -> Unit,
    onCellTap: (row: Int, col: Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Word Hunt · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val banner = when (uiState.result) {
                GameOutcome.WIN -> "You win! Claiming your coupon…"
                GameOutcome.LOSE -> "The computer wins this round"
                GameOutcome.DRAW -> "It's a draw!"
                else -> null
            }
            if (banner != null) {
                Text(text = banner, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            } else {
                TurnBanner(
                    text = if (uiState.turn == WordHuntSide.HUMAN) "Your turn - find a word" else "Computer is hunting…",
                    secondsLeft = uiState.secondsLeft,
                    note = if (uiState.lastTurnTimedOut) "(previous turn timed out)" else null,
                    isResolving = uiState.isResolving
                )
            }

            ScoreLine(myScore = uiState.humanScore, theirScore = uiState.computerScore, theirLabel = "Computer")

            WordList(
                words = uiState.words,
                myWords = uiState.humanClaims.map { it.word }.toSet(),
                theirWords = uiState.computerClaims.map { it.word }.toSet(),
                modifier = Modifier.padding(top = 12.dp)
            )

            LetterGrid(
                grid = uiState.grid,
                words = uiState.words,
                myWords = uiState.humanClaims.map { it.word }.toSet(),
                theirWords = uiState.computerClaims.map { it.word }.toSet(),
                selection = uiState.firstSelection,
                enabled = uiState.canHunt,
                onCellTap = onCellTap,
                modifier = Modifier.padding(top = 16.dp)
            )

            if (uiState.result == GameOutcome.LOSE || uiState.result == GameOutcome.DRAW) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

@Composable
fun TurnBanner(text: String, secondsLeft: Int, note: String?, isResolving: Boolean, modifier: Modifier = Modifier) {
    val isWarning = secondsLeft <= WORD_HUNT_TIME_WARNING_SECONDS
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = text, style = MaterialTheme.typography.titleMedium)
            note?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
        }
        Text(
            text = "${secondsLeft}s",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        if (isResolving) {
            CircularProgressIndicator(modifier = Modifier.padding(start = 10.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
fun ScoreLine(myScore: Int, theirScore: Int, theirLabel: String, modifier: Modifier = Modifier) {
    Text(
        text = "You $myScore  ·  $theirLabel $theirScore",
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp)
    )
    Text(
        text = "A word scores its length + half the seconds left on your clock.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun WordList(words: List<PlacedWord>, myWords: Set<String>, theirWords: Set<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        words.forEach { placed ->
            val claimed = placed.word in myWords || placed.word in theirWords
            Text(
                text = placed.word,
                style = MaterialTheme.typography.labelLarge,
                textDecoration = if (claimed) TextDecoration.LineThrough else TextDecoration.None,
                color = when (placed.word) {
                    in myWords -> MaterialTheme.colorScheme.tertiary
                    in theirWords -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

/** The letter grid; words you found are highlighted in one colour, your opponent's in another. */
@Composable
fun LetterGrid(
    grid: List<List<Char>>,
    words: List<PlacedWord>,
    myWords: Set<String>,
    theirWords: Set<String>,
    selection: Pair<Int, Int>?,
    enabled: Boolean,
    onCellTap: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val myCells = words.filter { it.word in myWords }.flatMap { it.cells() }.toSet()
    val theirCells = words.filter { it.word in theirWords }.flatMap { it.cells() }.toSet()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (row in grid.indices) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                for (col in grid[row].indices) {
                    val cell = row to col
                    val (background, onBackground) = when {
                        cell in myCells -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
                        cell in theirCells -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
                        selection == cell -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(background)
                            .clickable(enabled = enabled) { onCellTap(row, col) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = grid[row][col].toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (cell in myCells || cell in theirCells) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            color = onBackground
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordHuntScreenPreview() {
    PlayWinTheme {
        WordHuntScreen(
            uiState = WordHuntUiState(),
            onBack = {},
            onCellTap = { _, _ -> },
            onPlayAgain = {}
        )
    }
}
