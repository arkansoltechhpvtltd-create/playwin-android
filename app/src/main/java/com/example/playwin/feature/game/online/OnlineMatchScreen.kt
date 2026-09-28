package com.example.playwin.feature.game.online

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus

/** Which way a game is being played. There is deliberately no same-device two-player mode. */
enum class PlayMode { COMPUTER, ONLINE }

/** The shared "Play vs Computer" / "Find a random player" picker every game opens with. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameModeSelectScreen(
    title: String,
    onBack: () -> Unit,
    onSelectMode: (PlayMode) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { PwTopBar(title = title, onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "How do you want to play?", style = MaterialTheme.typography.titleLarge)
            PrimaryButton(
                text = "Play vs Computer",
                onClick = { onSelectMode(PlayMode.COMPUTER) },
                modifier = Modifier.padding(top = 24.dp)
            )
            PrimaryButton(
                text = "Find a random player",
                onClick = { onSelectMode(PlayMode.ONLINE) },
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

/**
 * Wires a game's online screen to its [OnlineMatchController]: opening the
 * route immediately starts a search (the caller's button already said "Find
 * a random player"), back - top bar or system - cancels the search or
 * forfeits the room, and a won room hands its coupon to [onWin].
 */
@Composable
fun OnlineMatchEffects(
    state: OnlineMatchState,
    controller: OnlineMatchController,
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoStarted) {
            autoStarted = true
            controller.findRandomPlayer()
        }
    }

    LaunchedEffect(state.wonCouponId) {
        state.wonCouponId?.let(onWin)
    }

    BackHandler {
        controller.exit()
        onBack()
    }
}

/**
 * The shared online screen: sign-in gate, searching, no-opponent-found,
 * abandoned and finished states are identical for every game; only the live
 * board ([activeContent]) and the final board ([finishedBoard]) differ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineMatchScreen(
    title: String,
    state: OnlineMatchState,
    controller: OnlineMatchController,
    onBack: () -> Unit,
    onPlayComputer: () -> Unit,
    activeContent: @Composable ColumnScope.(Room) -> Unit,
    finishedBoard: @Composable (Room) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            PwTopBar(title = title, onBack = {
                controller.exit()
                onBack()
            })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!state.canPlayOnline) {
                CenteredMessage(
                    title = "Sign in to play online",
                    body = "Guest sessions can play vs the computer, but online matches need a real account so we know who's who."
                )
            } else {
                when (state.phase) {
                    OnlinePhase.IDLE -> IdleContent(onFindRandomPlayer = controller::findRandomPlayer)
                    OnlinePhase.SEARCHING -> SearchingContent(state.searchSecondsElapsed, controller::cancelSearch)
                    OnlinePhase.NO_OPPONENT_FOUND -> NoOpponentContent(
                        onFindRandomPlayer = controller::findRandomPlayer,
                        onPlayComputer = {
                            controller.exit()
                            onPlayComputer()
                        }
                    )
                    OnlinePhase.ROOM -> state.room?.let { room ->
                        when (room.status) {
                            RoomStatus.ACTIVE -> ActiveContent(state, room, controller::leaveRoom, activeContent)
                            RoomStatus.FINISHED -> FinishedContent(state, room, controller::leaveRoom, finishedBoard)
                            RoomStatus.WAITING, RoomStatus.ABANDONED -> AbandonedContent(controller::leaveRoom)
                        }
                    }
                }
            }

            state.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = controller::dismissError) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun CenteredMessage(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun IdleContent(onFindRandomPlayer: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Play online", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You'll be paired with a random player.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(text = "Find a random player", onClick = onFindRandomPlayer)
    }
}

@Composable
private fun SearchingContent(secondsElapsed: Int, onCancelSearch: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Finding a random player…", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "${secondsElapsed}s / ${MATCHMAKING_TIMEOUT_SECONDS}s", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onCancelSearch) { Text("Cancel") }
    }
}

@Composable
private fun NoOpponentContent(
    onFindRandomPlayer: () -> Unit,
    onPlayComputer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "No opponent found", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Nobody else is searching right now. Try again, or play the computer.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(text = "Find a random player", onClick = onFindRandomPlayer)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onPlayComputer) { Text("Play vs Computer") }
    }
}

@Composable
private fun ActiveContent(
    state: OnlineMatchState,
    room: Room,
    onLeaveRoom: () -> Unit,
    content: @Composable ColumnScope.(Room) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (state.isMyTurn) "Your turn" else "Opponent's turn",
            style = MaterialTheme.typography.titleMedium
        )
        state.disconnectSecondsLeft?.let { secondsLeft ->
            Text(
                text = if (state.opponentEverSeen) {
                    "Opponent disconnected - game ends in ${secondsLeft}s unless they return"
                } else {
                    "Waiting for opponent to connect… (${secondsLeft}s)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        content(room)
        if (state.isResolving) {
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator(modifier = Modifier.height(20.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onLeaveRoom) { Text("Leave game") }
    }
}

@Composable
private fun FinishedContent(
    state: OnlineMatchState,
    room: Room,
    onLeaveRoom: () -> Unit,
    board: @Composable (Room) -> Unit,
    modifier: Modifier = Modifier
) {
    val text = when {
        room.winnerId == null -> "It's a draw!"
        room.winnerId == state.myId -> "You win! Claiming your coupon…"
        else -> "Opponent wins this round"
    }
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        board(room)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onLeaveRoom) { Text("Back") }
    }
}

@Composable
private fun AbandonedContent(onLeaveRoom: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Your opponent left or lost connection",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "This game has ended with no result.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onLeaveRoom) { Text("Back") }
    }
}
