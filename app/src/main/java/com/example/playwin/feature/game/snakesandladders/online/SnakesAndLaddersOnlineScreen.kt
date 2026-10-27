package com.example.playwin.feature.game.snakesandladders.online

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.feature.game.online.OnlineMatchEffects
import com.example.playwin.feature.game.online.OnlineMatchScreen
import com.example.playwin.feature.game.snakesandladders.SlGoldToken
import com.example.playwin.feature.game.snakesandladders.SlPearlToken
import com.example.playwin.feature.game.snakesandladders.SnakesAndLaddersBoard
import com.example.playwin.feature.game.snakesandladders.SnakesAndLaddersDock
import com.example.playwin.feature.game.snakesandladders.SnakesAndLaddersNote
import com.example.playwin.feature.game.snakesandladders.SnakesAndLaddersPlayers
import com.example.playwin.feature.game.snakesandladders.SnakesAndLaddersTiming
import com.example.playwin.feature.game.snakesandladders.boardDescription
import com.example.playwin.feature.game.snakesandladders.moveNote
import com.example.playwin.feature.game.snakesandladders.noteTone
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

            SnakesAndLaddersPlayers(
                names = names,
                positions = game.positions,
                tokenColors = colors,
                turn = game.turn,
                winner = null,
                modifier = Modifier.padding(top = 8.dp)
            )
            SnakesAndLaddersNote(
                text = moveNote(move, move?.let { names[it.player] }.orEmpty(), rollsAgain = move != null && game.turn == move.player)
                    ?: "Roll the die to set off from the start",
                tone = noteTone(move)
            )
            // The board takes all the height between the players and the dock.
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                SnakesAndLaddersBoard(
                    positions = game.positions,
                    lastMove = move,
                    tokenColors = colors,
                    description = boardDescription(names, game.positions),
                    activePlayer = game.turn.takeIf { !animating }
                )
            }
            SnakesAndLaddersDock(
                dieValue = move?.roll,
                rollCount = game.turnsPlayed,
                canRoll = canRoll,
                waitingLabel = if (state.isMyTurn) "Rolling…" else "Opponent's roll…",
                onRoll = viewModel::onRoll,
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
