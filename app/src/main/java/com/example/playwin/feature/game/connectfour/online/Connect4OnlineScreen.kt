package com.example.playwin.feature.game.connectfour.online

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.domain.model.RoomStatus
import com.example.playwin.feature.game.connectfour.Connect4Board

/**
 * Online Connect 4 via random matchmaking. Opening this route immediately
 * starts a search (the caller's button already said "Find a random player"),
 * so there's no menu step and no way to pick or invite an opponent.
 */
@Composable
fun Connect4OnlineRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    onPlayComputer: () -> Unit,
    viewModel: Connect4OnlineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoStarted) {
            autoStarted = true
            viewModel.findRandomPlayer()
        }
    }

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    val exit = {
        viewModel.exit()
        onBack()
    }
    BackHandler(onBack = exit)

    Connect4OnlineScreen(
        uiState = uiState,
        onBack = exit,
        onFindRandomPlayer = viewModel::findRandomPlayer,
        onCancelSearch = viewModel::cancelSearch,
        onPlayComputer = {
            viewModel.exit()
            onPlayComputer()
        },
        onColumnClick = viewModel::onColumnClick,
        onLeaveRoom = viewModel::leaveRoom,
        onDismissError = viewModel::dismissError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Connect4OnlineScreen(
    uiState: Connect4OnlineUiState,
    onBack: () -> Unit,
    onFindRandomPlayer: () -> Unit,
    onCancelSearch: () -> Unit,
    onPlayComputer: () -> Unit,
    onColumnClick: (Int) -> Unit,
    onLeaveRoom: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Connect 4 · Online", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!uiState.canPlayOnline) {
                SignInRequiredContent()
            } else {
                when (uiState.phase) {
                    Connect4OnlinePhase.IDLE -> IdleContent(onFindRandomPlayer)
                    Connect4OnlinePhase.SEARCHING -> SearchingContent(uiState.searchSecondsElapsed, onCancelSearch)
                    Connect4OnlinePhase.NO_OPPONENT_FOUND -> NoOpponentContent(onFindRandomPlayer, onPlayComputer)
                    Connect4OnlinePhase.ROOM -> RoomContent(uiState, onColumnClick, onLeaveRoom)
                }
            }

            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onDismissError) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun SignInRequiredContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sign in to play online",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Guest sessions can play vs the computer, but online matches need a real account so we know who's who.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun IdleContent(onFindRandomPlayer: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Play Connect 4 online", style = MaterialTheme.typography.titleLarge)
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
        Text(
            text = "${secondsElapsed}s / ${MATCHMAKING_TIMEOUT_SECONDS}s",
            style = MaterialTheme.typography.bodySmall
        )
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
private fun RoomContent(
    uiState: Connect4OnlineUiState,
    onColumnClick: (Int) -> Unit,
    onLeaveRoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val room = uiState.room ?: return
    when (room.status) {
        RoomStatus.ACTIVE -> ActiveGameContent(uiState, onColumnClick, onLeaveRoom, modifier)
        RoomStatus.FINISHED -> FinishedContent(uiState, onLeaveRoom, modifier)
        RoomStatus.WAITING, RoomStatus.ABANDONED -> AbandonedContent(onLeaveRoom, modifier)
    }
}

@Composable
private fun ActiveGameContent(
    uiState: Connect4OnlineUiState,
    onColumnClick: (Int) -> Unit,
    onLeaveRoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val room = uiState.room ?: return
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (uiState.isMyTurn) "Your turn" else "Opponent's turn",
            style = MaterialTheme.typography.titleMedium
        )
        uiState.disconnectSecondsLeft?.let { secondsLeft ->
            Text(
                text = if (uiState.opponentEverSeen) {
                    "Opponent disconnected - game ends in ${secondsLeft}s unless they return"
                } else {
                    "Waiting for opponent to connect… (${secondsLeft}s)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        Connect4Board(
            board = room.board,
            enabled = uiState.isMyTurn && !uiState.isResolving,
            onColumnClick = onColumnClick,
            modifier = Modifier.padding(top = 16.dp)
        )
        if (uiState.isResolving) {
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator(modifier = Modifier.height(20.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onLeaveRoom) { Text("Leave game") }
    }
}

@Composable
private fun FinishedContent(uiState: Connect4OnlineUiState, onLeaveRoom: () -> Unit, modifier: Modifier = Modifier) {
    val room = uiState.room ?: return
    val text = when {
        room.winnerId == null -> "It's a draw!"
        room.winnerId == uiState.myId -> "You win! Claiming your coupon…"
        else -> "Opponent wins this round"
    }
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Connect4Board(board = room.board, enabled = false, onColumnClick = {})
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
        Text(
            text = "This game has ended with no result.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onLeaveRoom) { Text("Back") }
    }
}
