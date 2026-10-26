package com.example.playwin.feature.game.snakesandladders

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersRules
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.feature.game.GameScaffold
import com.example.playwin.feature.game.GameStatusBanner
import com.example.playwin.feature.game.ludo.LudoDice
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
import com.example.playwin.feature.game.snakesandladders.online.SnakesAndLaddersOnlineRoute

/** Entry point for Snakes & Ladders: play the computer, or get paired with a random online player. */
@Composable
fun SnakesAndLaddersRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<PlayMode?>(null) }
    when (mode) {
        null -> GameModeSelectScreen(title = "Snakes & Ladders", onBack = onBack, onSelectMode = { mode = it })
        PlayMode.COMPUTER -> SnakesAndLaddersComputerRoute(onBack = { mode = null }, onWin = onWin)
        PlayMode.ONLINE -> SnakesAndLaddersOnlineRoute(
            onBack = { mode = null },
            onWin = onWin,
            onPlayComputer = { mode = PlayMode.COMPUTER }
        )
    }
}

@Composable
private fun SnakesAndLaddersComputerRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: SnakesAndLaddersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    SnakesAndLaddersScreen(
        uiState = uiState,
        onBack = onBack,
        onRoll = viewModel::onRoll,
        onPlayAgain = viewModel::onPlayAgain
    )
}

private val COMPUTER_TOKEN_COLORS = listOf(SlGoldToken, SlPearlToken)

@Composable
fun SnakesAndLaddersScreen(
    uiState: SnakesAndLaddersUiState,
    onBack: () -> Unit,
    onRoll: () -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val names = listOf("You", "Computer")
    GameScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Snakes & Ladders · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val move = uiState.lastMove
            val winner = uiState.winner
            if (winner != null) {
                SnakesAndLaddersWinnerBanner(
                    text = if (winner == SL_HUMAN) "You win! Claiming your coupon…" else "The computer wins this round",
                    won = winner == SL_HUMAN,
                    tokenColors = COMPUTER_TOKEN_COLORS[winner]
                )
            } else {
                SnakesAndLaddersTurnBanner(
                    title = if (uiState.isHumanTurn) "Your roll" else "Computer's roll",
                    note = moveNote(move, move?.let { names[it.player] }.orEmpty(), rollsAgain = move != null && uiState.game.turn == move.player),
                    tokenColors = COMPUTER_TOKEN_COLORS[uiState.game.turn]
                )
            }
            PlayerSquares(
                names = names,
                positions = uiState.game.positions,
                tokenColors = COMPUTER_TOKEN_COLORS,
                modifier = Modifier.padding(top = 10.dp)
            )

            BoardSlot(modifier = Modifier.padding(top = 12.dp)) {
                SnakesAndLaddersBoard(
                    positions = uiState.game.positions,
                    lastMove = move,
                    tokenColors = COMPUTER_TOKEN_COLORS,
                    description = boardDescription(names, uiState.game.positions),
                    activePlayer = uiState.game.turn.takeIf { winner == null && !uiState.isBusy },
                    celebrate = winner == SL_HUMAN
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Keyed by the roll count so two equal rolls in a row still tumble.
                key(uiState.game.turnsPlayed) {
                    LudoDice(
                        value = move?.roll,
                        live = uiState.isBusy,
                        enabled = uiState.canRoll,
                        onClick = onRoll
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                PrimaryButton(text = "Roll", onClick = onRoll, enabled = uiState.canRoll)
                if (uiState.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp).size(18.dp), strokeWidth = 2.dp)
                }
            }

            if (winner == SL_COMPUTER) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

/** The turn line: whose roll it is, with what the last roll did underneath. */
@Composable
fun SnakesAndLaddersTurnBanner(title: String, note: String?, tokenColors: List<Color>, modifier: Modifier = Modifier) {
    GameStatusBanner(accent = tokenColors[1], modifier = modifier) {
        TokenIcon(tokenColors, Modifier.size(24.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SnakesAndLaddersWinnerBanner(text: String, won: Boolean, tokenColors: List<Color>, modifier: Modifier = Modifier) {
    val gold = MaterialTheme.colorScheme.primary
    GameStatusBanner(accent = if (won) gold else tokenColors[1], modifier = modifier) {
        if (won) {
            Icon(imageVector = Icons.Filled.EmojiEvents, contentDescription = null, tint = gold, modifier = Modifier.size(26.dp))
        } else {
            TokenIcon(tokenColors, Modifier.size(24.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )
    }
}

/** Each player's token and the square it's on. */
@Composable
fun PlayerSquares(
    names: List<String>,
    positions: List<Int>,
    tokenColors: List<List<Color>>,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        names.forEachIndexed { player, name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TokenIcon(tokenColors[player], Modifier.size(16.dp))
                Text(
                    text = "$name · ${squareLabel(positions[player])}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun TokenIcon(colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(colors, center = center - Offset(r * 0.35f, r * 0.4f), radius = r * 1.6f), r)
        drawCircle(Color.Black.copy(alpha = 0.45f), r, style = Stroke(width = r * 0.14f))
    }
}

private fun squareLabel(square: Int): String =
    if (square <= SnakesAndLaddersRules.START) "start" else "square $square"

fun boardDescription(names: List<String>, positions: List<Int>): String =
    "Snakes and Ladders board. " + names.indices.joinToString(", ") { "${names[it]} on ${squareLabel(positions[it])}" }

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SnakesAndLaddersScreenPreview() {
    PlayWinTheme {
        SnakesAndLaddersScreen(
            uiState = SnakesAndLaddersUiState(),
            onBack = {},
            onRoll = {},
            onPlayAgain = {}
        )
    }
}
