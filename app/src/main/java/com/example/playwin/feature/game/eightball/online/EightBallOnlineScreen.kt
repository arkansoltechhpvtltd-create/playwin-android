package com.example.playwin.feature.game.eightball.online

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.feature.game.PhysicsFrameClock
import com.example.playwin.core.domain.eightball.EightBallFoul
import com.example.playwin.feature.game.eightball.GroupLegend
import com.example.playwin.feature.game.eightball.PoolTable
import com.example.playwin.feature.game.eightball.TurnBanner
import com.example.playwin.feature.game.online.OnlineMatchEffects
import com.example.playwin.feature.game.online.OnlineMatchScreen

/** Online 8-ball via random matchmaking - starts searching as soon as it opens. */
@Composable
fun EightBallOnlineRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    onPlayComputer: () -> Unit,
    viewModel: EightBallOnlineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val balls by viewModel.displayBalls.collectAsStateWithLifecycle()
    val isAnimating by viewModel.isAnimating.collectAsStateWithLifecycle()
    val secondsLeft by viewModel.secondsLeft.collectAsStateWithLifecycle()
    OnlineMatchEffects(state, viewModel.controller, onBack, onWin)
    PhysicsFrameClock(onTick = viewModel::onPhysicsTick)

    OnlineMatchScreen(
        title = "8-Ball · Online",
        state = state,
        controller = viewModel.controller,
        onBack = onBack,
        onPlayComputer = onPlayComputer,
        activeContent = { room ->
            val me = state.myPlayerNumber ?: return@OnlineMatchScreen
            val stored = room.eightBallState()

            TurnBanner(
                text = when {
                    isAnimating -> "…"
                    state.isMyTurn -> "Your shot - drag back from the cue ball"
                    else -> "Opponent is aiming"
                } + (stored.foul?.let { name -> runCatching { EightBallFoul.valueOf(name) }.getOrNull() }
                    ?.let { " (foul on previous shot: ${it.label})" } ?: ""),
                secondsLeft = secondsLeft,
                isBusy = isAnimating || state.isResolving,
                modifier = Modifier.padding(top = 8.dp)
            )
            BoardSlot(modifier = Modifier.padding(top = 12.dp)) {
                PoolTable(
                    balls = balls,
                    canAim = state.isMyTurn && !state.isResolving && !isAnimating,
                    onShoot = viewModel::onShoot
                )
            }
            val match = stored.toMatch()
            GroupLegend(myGroup = match.playerGroups[me], modifier = Modifier.padding(top = 12.dp), beforeBreak = match.isBreak)
        },
        finishedBoard = { room ->
            BoardSlot {
                PoolTable(balls = room.eightBallState().toMatch().balls, canAim = false, onShoot = { _, _ -> })
            }
        }
    )
}
