package com.example.playwin.feature.game.bingo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.domain.bingo.BINGO_FREE_COL
import com.example.playwin.core.domain.bingo.BINGO_FREE_ROW
import com.example.playwin.core.domain.bingo.BINGO_LETTERS
import com.example.playwin.core.domain.bingo.BINGO_SIZE
import com.example.playwin.core.domain.bingo.bingoLetterFor
import com.example.playwin.feature.game.bingo.online.BingoOnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode

/** Entry point for Bingo: play the computer, or get paired with a random online player. */
@Composable
fun BingoRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<PlayMode?>(null) }
    when (mode) {
        null -> GameModeSelectScreen(title = "Bingo", onBack = onBack, onSelectMode = { mode = it })
        PlayMode.COMPUTER -> BingoComputerRoute(onBack = { mode = null }, onWin = onWin)
        PlayMode.ONLINE -> BingoOnlineRoute(
            onBack = { mode = null },
            onWin = onWin,
            onPlayComputer = { mode = PlayMode.COMPUTER }
        )
    }
}

@Composable
private fun BingoComputerRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: BingoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    BingoScreen(
        uiState = uiState,
        onBack = onBack,
        onCallNumber = viewModel::onCallNumber,
        onCallRandom = viewModel::onCallRandom,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BingoScreen(
    uiState: BingoUiState,
    onBack: () -> Unit,
    onCallNumber: (Int) -> Unit,
    onCallRandom: () -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Bingo · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val banner = when {
                uiState.winner == BingoSide.HUMAN -> "Bingo! Claiming your coupon…"
                uiState.winner == BingoSide.COMPUTER -> "The computer got bingo first"
                uiState.turn == BingoSide.HUMAN -> "Your call - tap a number on your card"
                else -> "Computer is calling…"
            }
            Text(text = banner, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            LastCallText(uiState.lastCall, uiState.lastCaller?.let { if (it == BingoSide.HUMAN) "You" else "Computer" })

            CalledNumbersRow(uiState.called, modifier = Modifier.padding(top = 8.dp))

            BingoCardView(
                card = uiState.myCard,
                called = uiState.called,
                onCall = if (uiState.canCall) onCallNumber else null,
                modifier = Modifier.padding(top = 16.dp)
            )

            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onCallRandom, enabled = uiState.canCall) { Text("Call a random number") }
                if (uiState.isResolving) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp).size(18.dp), strokeWidth = 2.dp)
                }
            }

            OpponentCard(label = "Computer's card", card = uiState.computerCard, called = uiState.called)

            if (uiState.winner == BingoSide.COMPUTER) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

@Composable
fun LastCallText(lastCall: Int?, caller: String?, modifier: Modifier = Modifier) {
    val text = if (lastCall != null && caller != null) "$caller called ${bingoLetterFor(lastCall)}-$lastCall" else "No numbers called yet"
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(top = 4.dp)
    )
}

@Composable
fun OpponentCard(label: String, card: List<List<Int>>, called: List<Int>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        BingoCardView(
            card = card,
            called = called,
            onCall = null,
            showHeader = false,
            modifier = Modifier.width(180.dp).padding(top = 6.dp)
        )
    }
}

@Composable
fun CalledNumbersRow(calledNumbers: List<Int>, modifier: Modifier = Modifier) {
    if (calledNumbers.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(calledNumbers.asReversed()) { number ->
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${bingoLetterFor(number)}-$number",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * A bingo card with every [called] number marked. When [onCall] is non-null,
 * tapping an uncalled number on the card calls it.
 */
@Composable
fun BingoCardView(
    card: List<List<Int>>,
    called: List<Int>,
    onCall: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (showHeader) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                BINGO_LETTERS.forEach { letter ->
                    Text(
                        text = letter,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        for (row in 0 until BINGO_SIZE) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until BINGO_SIZE) {
                    val isFree = row == BINGO_FREE_ROW && col == BINGO_FREE_COL
                    val number = card[row][col]
                    val isMarked = isFree || number in called
                    val tappable = onCall != null && !isMarked

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isMarked -> MaterialTheme.colorScheme.tertiary
                                    tappable -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                            .clickable(enabled = tappable) { onCall?.invoke(number) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isFree) "FREE" else number.toString(),
                            style = if (showHeader) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
                            fontWeight = if (isMarked) FontWeight.Bold else FontWeight.Normal,
                            color = if (isMarked) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun BingoScreenPreview() {
    PlayWinTheme {
        BingoScreen(
            uiState = BingoUiState(),
            onBack = {},
            onCallNumber = {},
            onCallRandom = {},
            onPlayAgain = {}
        )
    }
}
