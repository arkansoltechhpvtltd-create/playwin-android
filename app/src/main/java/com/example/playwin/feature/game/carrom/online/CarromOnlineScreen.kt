package com.example.playwin.feature.game.carrom.online

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.feature.game.carrom.CarromBoardView
import com.example.playwin.feature.game.PhysicsFrameClock
import com.example.playwin.feature.game.carrom.ScoreLegend
import com.example.playwin.feature.game.carrom.TurnBanner
import com.example.playwin.feature.game.online.OnlineMatchEffects
import com.example.playwin.feature.game.online.OnlineMatchScreen

/** Online carrom via random matchmaking - starts searching as soon as it opens. */
@Composable
fun CarromOnlineRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    onPlayComputer: () -> Unit,
    viewModel: CarromOnlineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val coins by viewModel.displayCoins.collectAsStateWithLifecycle()
    val isAnimating by viewModel.isAnimating.collectAsStateWithLifecycle()
    val secondsLeft by viewModel.secondsLeft.collectAsStateWithLifecycle()
    OnlineMatchEffects(state, viewModel.controller, onBack, onWin)
    PhysicsFrameClock(onTick = viewModel::onPhysicsTick)

    OnlineMatchScreen(
        title = "Carrom · Online",
        state = state,
        controller = viewModel.controller,
        onBack = onBack,
        onPlayComputer = onPlayComputer,
        activeContent = { room ->
            val me = state.myPlayerNumber ?: return@OnlineMatchScreen
            val stored = room.carromState()
            val match = stored.toMatch()

            TurnBanner(
                text = when {
                    isAnimating -> "…"
                    state.isMyTurn -> "Your shot - drag back from the striker"
                    else -> "Opponent is aiming"
                } + if (stored.fouled) " (foul on previous shot)" else "",
                secondsLeft = secondsLeft,
                isBusy = isAnimating || state.isResolving,
                modifier = Modifier.padding(top = 8.dp)
            )
            CarromBoardView(
                coins = coins,
                canAim = state.isMyTurn && !state.isResolving && !isAnimating,
                onShoot = viewModel::onShoot,
                flipped = me == 2,
                modifier = Modifier.padding(top = 12.dp)
            )
            ScoreLegend(
                myColor = match.playerColors[me],
                myScore = match.scores.getValue(me),
                theirScore = match.scores.getValue(3 - me),
                theirLabel = "Opponent",
                modifier = Modifier.padding(top = 12.dp)
            )
        },
        finishedBoard = { room ->
            val me = state.myPlayerNumber ?: 1
            val match = room.carromState().toMatch()
            CarromBoardView(coins = match.coins, canAim = false, onShoot = { _, _ -> }, flipped = me == 2)
            ScoreLegend(
                myColor = match.playerColors[me],
                myScore = match.scores.getValue(me),
                theirScore = match.scores.getValue(3 - me),
                theirLabel = "Opponent",
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    )
}
