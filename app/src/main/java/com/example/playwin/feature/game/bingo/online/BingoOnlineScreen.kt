package com.example.playwin.feature.game.bingo.online

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.feature.game.bingo.BingoCardView
import com.example.playwin.feature.game.bingo.CalledNumbersRow
import com.example.playwin.feature.game.bingo.LastCallText
import com.example.playwin.feature.game.bingo.OpponentCard
import com.example.playwin.feature.game.online.OnlineMatchEffects
import com.example.playwin.feature.game.online.OnlineMatchScreen

/** Online head-to-head bingo via random matchmaking - starts searching as soon as it opens. */
@Composable
fun BingoOnlineRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    onPlayComputer: () -> Unit,
    viewModel: BingoOnlineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnlineMatchEffects(state, viewModel.controller, onBack, onWin)

    OnlineMatchScreen(
        title = "Bingo · Online",
        state = state,
        controller = viewModel.controller,
        onBack = onBack,
        onPlayComputer = onPlayComputer,
        activeContent = { room ->
            val me = state.myPlayerNumber ?: return@OnlineMatchScreen
            val bingo = room.bingoState()
            val canCall = state.isMyTurn && !state.isResolving

            LastCallText(
                lastCall = bingo.called.lastOrNull(),
                caller = bingo.lastCaller?.let { if (it == me) "You" else "Opponent" }
            )
            CalledNumbersRow(bingo.called, modifier = Modifier.padding(top = 8.dp))
            BingoCardView(
                card = bingoCardFor(room, me),
                called = bingo.called,
                onCall = if (canCall) viewModel::onCallNumber else null,
                modifier = Modifier.padding(top = 12.dp)
            )
            OutlinedButton(
                onClick = viewModel::onCallRandom,
                enabled = canCall,
                modifier = Modifier.padding(top = 12.dp)
            ) { Text("Call a random number") }
            OpponentCard(label = "Opponent's card", card = bingoCardFor(room, 3 - me), called = bingo.called)
        },
        finishedBoard = { room ->
            val me = state.myPlayerNumber ?: 1
            BingoCardView(card = bingoCardFor(room, me), called = room.bingoState().called, onCall = null)
        }
    )
}
