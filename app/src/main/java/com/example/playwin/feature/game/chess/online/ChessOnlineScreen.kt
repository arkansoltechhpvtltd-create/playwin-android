package com.example.playwin.feature.game.chess.online

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.domain.chess.ChessEngine
import com.example.playwin.core.domain.chess.PieceColor
import com.example.playwin.feature.game.chess.ChessBoard
import com.example.playwin.feature.game.online.OnlineMatchEffects
import com.example.playwin.feature.game.online.OnlineMatchScreen

/** Online chess via random matchmaking - starts searching as soon as it opens. */
@Composable
fun ChessOnlineRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    onPlayComputer: () -> Unit,
    viewModel: ChessOnlineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    OnlineMatchEffects(state, viewModel.controller, onBack, onWin)

    OnlineMatchScreen(
        title = "Chess · Online",
        state = state,
        controller = viewModel.controller,
        onBack = onBack,
        onPlayComputer = onPlayComputer,
        activeContent = { room ->
            val me = state.myPlayerNumber ?: return@OnlineMatchScreen
            val position = room.chessPosition()
            val myColor = chessColorOf(me)
            val inCheck = ChessEngine.isInCheck(position, position.sideToMove)

            Text(
                text = "You play ${if (myColor == PieceColor.WHITE) "White" else "Black"}" + if (inCheck) " - check!" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (inCheck) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            ChessBoard(
                position = position,
                selectedSquare = selection.square,
                legalTargets = selection.moves.map { it.to },
                enabled = state.isMyTurn && !state.isResolving,
                onSquareTap = viewModel::onSquareTap,
                flipped = myColor == PieceColor.BLACK,
                modifier = Modifier.padding(top = 12.dp)
            )
        },
        finishedBoard = { room ->
            ChessBoard(
                position = room.chessPosition(),
                selectedSquare = null,
                legalTargets = emptyList(),
                enabled = false,
                onSquareTap = {},
                flipped = state.myPlayerNumber == 2
            )
        }
    )
}
