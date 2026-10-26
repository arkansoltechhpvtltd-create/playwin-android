package com.example.playwin.feature.game.snakesandladders.online

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.feature.game.ludo.LudoDice
import com.example.playwin.feature.game.online.OnlineMatchEffects
import com.example.playwin.feature.game.online.OnlineMatchScreen
import com.example.playwin.feature.game.snakesandladders.PlayerSquares
import com.example.playwin.feature.game.snakesandladders.SlGoldToken
import com.example.playwin.feature.game.snakesandladders.SlPearlToken
import com.example.playwin.feature.game.snakesandladders.SnakesAndLaddersBoard
import com.example.playwin.feature.game.snakesandladders.SnakesAndLaddersTiming
import com.example.playwin.feature.game.snakesandladders.boardDescription
import com.example.playwin.feature.game.snakesandladders.moveNote
import kotlinx.coroutines.delay

/** Online 1v1 Snakes & Ladders via random matchmaking - starts searching as soon as it opens. */
@Composable
fun SnakesAndLaddersOnlineRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    onPlayComputer: () -> Unit,
    viewModel: SnakesAndLaddersOnlineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnlineMatchEffects(state, viewModel.controller, onBack, onWin)

    OnlineMatchScreen(
        title = "Snakes & Ladders · Online",
        state = state,
        controller = viewModel.controller,
        onBack = onBack,
        onPlayComputer = onPlayComputer,
        activeContent = { room ->
            val me = state.myPlayerNumber?.let(::snakesAndLaddersPlayerIndex) ?: return@OnlineMatchScreen
            val game = room.snakesAndLaddersState()
            val move = room.snakesAndLaddersLastMove()
            // Both players see their own token gold.
            val names = List(game.positions.size) { if (it == me) "You" else "Opponent" }
            val colors = List(game.positions.size) { if (it == me) SlGoldToken else SlPearlToken }

            // Hold the next roll until the last one has played out on the board.
            var animating by remember { mutableStateOf(false) }
            LaunchedEffect(game.turnsPlayed) {
                animating = true
                delay(SnakesAndLaddersTiming.moveMillis(move))
                animating = false
            }
            val canRoll = state.isMyTurn && !state.isResolving && !animating

            moveNote(move, move?.let { names[it.player] }.orEmpty(), rollsAgain = move != null && game.turn == move.player)?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PlayerSquares(names = names, positions = game.positions, tokenColors = colors, modifier = Modifier.padding(top = 8.dp))
            BoardSlot(modifier = Modifier.padding(top = 12.dp)) {
                SnakesAndLaddersBoard(
                    positions = game.positions,
                    lastMove = move,
                    tokenColors = colors,
                    description = boardDescription(names, game.positions),
                    activePlayer = game.turn.takeIf { !animating }
                )
            }
            key(game.turnsPlayed) {
                LudoDice(
                    value = move?.roll,
                    live = animating,
                    enabled = canRoll,
                    onClick = viewModel::onRoll,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            PrimaryButton(
                text = "Roll",
                onClick = viewModel::onRoll,
                enabled = canRoll,
                modifier = Modifier.padding(top = 12.dp)
            )
        },
        finishedBoard = { room ->
            val me = state.myPlayerNumber?.let(::snakesAndLaddersPlayerIndex) ?: 0
            val game = room.snakesAndLaddersState()
            val names = List(game.positions.size) { if (it == me) "You" else "Opponent" }
            BoardSlot {
                SnakesAndLaddersBoard(
                    positions = game.positions,
                    lastMove = room.snakesAndLaddersLastMove(),
                    tokenColors = List(game.positions.size) { if (it == me) SlGoldToken else SlPearlToken },
                    description = boardDescription(names, game.positions)
                )
            }
        }
    )
}
