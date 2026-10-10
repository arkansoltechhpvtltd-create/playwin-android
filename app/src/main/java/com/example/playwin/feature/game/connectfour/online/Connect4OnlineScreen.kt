package com.example.playwin.feature.game.connectfour.online

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.feature.game.connectfour.Connect4Board
import com.example.playwin.feature.game.online.OnlineMatchEffects
import com.example.playwin.feature.game.online.OnlineMatchScreen

/** Online Connect 4 via random matchmaking - starts searching as soon as it opens. */
@Composable
fun Connect4OnlineRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    onPlayComputer: () -> Unit,
    viewModel: Connect4OnlineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnlineMatchEffects(state, viewModel.controller, onBack, onWin)

    OnlineMatchScreen(
        title = "Connect 4 · Online",
        state = state,
        controller = viewModel.controller,
        onBack = onBack,
        onPlayComputer = onPlayComputer,
        activeContent = { room ->
            BoardSlot(modifier = Modifier.padding(top = 16.dp)) {
                Connect4Board(
                    board = room.connect4Board(),
                    enabled = state.isMyTurn && !state.isResolving,
                    onColumnClick = viewModel::onColumnClick
                )
            }
        },
        finishedBoard = { room ->
            BoardSlot { Connect4Board(board = room.connect4Board(), enabled = false, onColumnClick = {}) }
        }
    )
}
