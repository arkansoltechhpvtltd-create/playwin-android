package com.example.playwin.feature.game.chinesecheckers.online

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersBoard
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersRules
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.feature.game.chinesecheckers.PegIcon
import com.example.playwin.feature.game.chinesecheckers.StarBoard
import com.example.playwin.feature.game.online.OnlineMatchEffects
import com.example.playwin.feature.game.online.OnlineMatchScreen

/** Online Chinese checkers via random matchmaking - starts searching as soon as it opens. */
@Composable
fun ChineseCheckersOnlineRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    onPlayComputer: () -> Unit,
    viewModel: ChineseCheckersOnlineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    OnlineMatchEffects(state, viewModel.controller, onBack, onWin)

    OnlineMatchScreen(
        title = "Chinese Checkers · Online",
        state = state,
        controller = viewModel.controller,
        onBack = onBack,
        onPlayComputer = onPlayComputer,
        activeContent = { room ->
            val me = state.myPlayerNumber ?: return@OnlineMatchScreen
            val position = room.chineseCheckersPosition()
            val opponent = ChineseCheckersBoard.opponent(me)

            // Both players see their own pegs gold, starting at the bottom.
            Row(verticalAlignment = Alignment.CenterVertically) {
                PegIcon(mine = true, modifier = Modifier.size(16.dp))
                Text(
                    text = "You're gold · ${ChineseCheckersRules.pegsInGoal(position, me)}/10 home · " +
                        "opponent ${ChineseCheckersRules.pegsInGoal(position, opponent)}/10",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            BoardSlot(modifier = Modifier.padding(top = 12.dp)) {
                StarBoard(
                    position = position,
                    me = me,
                    selected = selection.hole,
                    targets = selection.moves.map { it.to },
                    lastMove = room.chineseCheckersLastMove(),
                    moveKey = position.turnsPlayed,
                    enabled = state.isMyTurn && !state.isResolving,
                    onHoleTap = viewModel::onHoleTap
                )
            }
        },
        finishedBoard = { room ->
            val position = room.chineseCheckersPosition()
            BoardSlot {
                StarBoard(
                    position = position,
                    me = state.myPlayerNumber ?: 1,
                    selected = null,
                    targets = emptyList(),
                    lastMove = room.chineseCheckersLastMove(),
                    moveKey = position.turnsPlayed,
                    enabled = false,
                    onHoleTap = {}
                )
            }
        }
    )
}
