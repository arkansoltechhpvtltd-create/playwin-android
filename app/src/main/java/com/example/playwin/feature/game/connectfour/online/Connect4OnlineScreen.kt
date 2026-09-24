package com.example.playwin.feature.game.connectfour.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.domain.model.RoomStatus
import com.example.playwin.feature.game.connectfour.Connect4Board

@Composable
fun Connect4OnlineRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: Connect4OnlineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    Connect4OnlineScreen(
        uiState = uiState,
        onBack = {
            if (uiState.phase == Connect4OnlinePhase.ROOM) viewModel.leaveRoom() else onBack()
        },
        onCreateRoom = viewModel::createRoom,
        onShowEnterCode = viewModel::showEnterCode,
        onJoinRoom = viewModel::joinRoom,
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
    onCreateRoom: () -> Unit,
    onShowEnterCode: () -> Unit,
    onJoinRoom: (String) -> Unit,
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
                    Connect4OnlinePhase.MENU -> MenuContent(onCreateRoom, onShowEnterCode)
                    Connect4OnlinePhase.ENTER_CODE -> EnterCodeContent(onJoinRoom)
                    Connect4OnlinePhase.CREATING, Connect4OnlinePhase.JOINING -> LoadingContent(
                        text = if (uiState.phase == Connect4OnlinePhase.CREATING) "Creating room…" else "Joining room…"
                    )
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
            text = "Guest sessions can play locally, but online rooms need a real account so we know who's who.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MenuContent(onCreateRoom: () -> Unit, onShowEnterCode: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Play Connect 4 online", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(text = "Create room", onClick = onCreateRoom)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onShowEnterCode) { Text("Join room") }
    }
}

@Composable
private fun EnterCodeContent(onJoinRoom: (String) -> Unit, modifier: Modifier = Modifier) {
    var code by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Enter room code", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(6) },
            label = { Text("Code") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryButton(text = "Join", onClick = { onJoinRoom(code) })
    }
}

@Composable
private fun LoadingContent(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = text)
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
        RoomStatus.WAITING -> WaitingContent(code = room.code, onLeaveRoom = onLeaveRoom, modifier = modifier)
        RoomStatus.ACTIVE -> ActiveGameContent(uiState, onColumnClick, onLeaveRoom, modifier)
        RoomStatus.FINISHED -> FinishedContent(uiState, onLeaveRoom, modifier)
        RoomStatus.ABANDONED -> AbandonedContent(onLeaveRoom, modifier)
    }
}

@Composable
private fun WaitingContent(code: String, onLeaveRoom: () -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Waiting for opponent…", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Share this code:", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = code,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            )
            IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy code")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onLeaveRoom) { Text("Cancel") }
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
        if (!uiState.opponentOnline) {
            Text(
                text = "Opponent seems to have disconnected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
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
        Button(onClick = onLeaveRoom) { Text("Back to menu") }
    }
}

@Composable
private fun AbandonedContent(onLeaveRoom: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Opponent left the game", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onLeaveRoom) { Text("Back to menu") }
    }
}
