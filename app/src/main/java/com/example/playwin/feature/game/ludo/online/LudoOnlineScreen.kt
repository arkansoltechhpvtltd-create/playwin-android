package com.example.playwin.feature.game.ludo.online

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.domain.ludo.LudoEngine
import com.example.playwin.feature.game.ludo.LudoBoard
import com.example.playwin.feature.game.ludo.colorFor
import com.example.playwin.feature.game.ludo.label
import com.example.playwin.feature.game.online.OnlineMatchEffects
import com.example.playwin.feature.game.online.OnlineMatchScreen

/** Online 1v1 Ludo via random matchmaking - starts searching as soon as it opens. */
@Composable
fun LudoOnlineRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    onPlayComputer: () -> Unit,
    viewModel: LudoOnlineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnlineMatchEffects(state, viewModel.controller, onBack, onWin)

    OnlineMatchScreen(
        title = "Ludo · Online",
        state = state,
        controller = viewModel.controller,
        onBack = onBack,
        onPlayComputer = onPlayComputer,
        activeContent = { room ->
            val game = room.ludoState()
            val myColor = state.myPlayerNumber?.let(::ludoColorOf)
            val canAct = state.isMyTurn && !state.isResolving
            val movable = if (canAct) LudoEngine.movableTokens(game).filter { it.color == myColor } else emptyList()

            myColor?.let {
                Text(text = "You are ${it.label()}", color = colorFor(it), style = MaterialTheme.typography.bodyMedium)
            }
            LudoBoard(
                game = game,
                movableTokens = movable,
                onTokenTap = viewModel::onTokenTap,
                modifier = Modifier.padding(top = 12.dp)
            )
            val rollText = when {
                game.diceValue != null -> "Rolled: ${game.diceValue} - tap a token"
                else -> room.ludoLastRoll()?.let { "Roll dice (last roll: $it)" } ?: "Roll dice"
            }
            PrimaryButton(
                text = rollText,
                onClick = viewModel::onRollDice,
                enabled = canAct && game.diceValue == null,
                modifier = Modifier.padding(top = 16.dp)
            )
        },
        finishedBoard = { room ->
            LudoBoard(game = room.ludoState(), movableTokens = emptyList(), onTokenTap = {})
        }
    )
}
