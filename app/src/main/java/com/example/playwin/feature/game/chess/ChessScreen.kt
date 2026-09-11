package com.example.playwin.feature.game.chess

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.domain.chess.ChessPiece
import com.example.playwin.core.domain.chess.PieceColor
import com.example.playwin.core.domain.chess.PieceType
import com.example.playwin.core.domain.chess.Square

@Composable
fun ChessRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: ChessViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    ChessScreen(
        uiState = uiState,
        onBack = onBack,
        onSquareTap = viewModel::onSquareTap,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessScreen(
    uiState: ChessUiState,
    onBack: () -> Unit,
    onSquareTap: (Square) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Chess", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                uiState.isDraw -> StatusBanner(text = "Stalemate - it's a draw!")
                uiState.winner != null -> StatusBanner(
                    text = "${uiState.winner.toPlayerLabel()} wins by checkmate! Claiming your coupon…"
                )
                else -> TurnBanner(uiState)
            }

            ChessBoardView(
                uiState = uiState,
                onSquareTap = onSquareTap,
                modifier = Modifier.padding(top = 16.dp)
            )

            if (uiState.winner != null || uiState.isDraw) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

private fun Int.toPlayerLabel(): String = if (this == 1) "White" else "Black"

@Composable
private fun TurnBanner(uiState: ChessUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${uiState.currentPlayer.toPlayerLabel()}'s turn" + if (uiState.isInCheck) " - check!" else "",
            style = MaterialTheme.typography.titleMedium,
            color = if (uiState.isInCheck) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (uiState.isResolving) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun StatusBanner(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier.fillMaxWidth())
}

private val LightSquare = Color(0xFFEEEED2)
private val DarkSquare = Color(0xFF769656)
private val SelectedSquare = Color(0xFFF6F669)
private val TargetSquare = Color(0xFFBACA44)

// Pieces get their own fixed light/dark colors with a contrasting outline so
// the two sides stay obviously distinct on any square, in either app theme -
// they must never rely on the current theme's text color.
private val WhitePieceFill = Color(0xFFFAFAF2)
private val WhitePieceOutline = Color(0xFF2B2B2B)
private val BlackPieceFill = Color(0xFF2B2B2B)
private val BlackPieceOutline = Color(0xFFFAFAF2)

@Composable
private fun ChessBoardView(
    uiState: ChessUiState,
    onSquareTap: (Square) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = uiState.winner == null && !uiState.isDraw && !uiState.isResolving

    Column(modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        for (row in 7 downTo 0) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0..7) {
                    val square = Square(row, col)
                    val piece = uiState.position.pieceAt(square)
                    val isDark = (row + col) % 2 == 0
                    val isSelected = uiState.selectedSquare == square
                    val isTarget = square in uiState.legalTargets

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                when {
                                    isSelected -> SelectedSquare
                                    isTarget -> TargetSquare
                                    isDark -> DarkSquare
                                    else -> LightSquare
                                }
                            )
                            .clickable(enabled = enabled) { onSquareTap(square) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (piece != null) {
                            val isWhite = piece.color == PieceColor.WHITE
                            val fill = if (isWhite) WhitePieceFill else BlackPieceFill
                            val outline = if (isWhite) WhitePieceOutline else BlackPieceOutline
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(0.76f)
                                    .background(fill, CircleShape)
                                    .border(1.5.dp, outline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = glyphFor(piece), fontSize = 20.sp, color = outline)
                            }
                        } else if (isTarget) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color.Black.copy(alpha = 0.25f), shape = CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun glyphFor(piece: ChessPiece): String = when (piece.color) {
    PieceColor.WHITE -> when (piece.type) {
        PieceType.KING -> "♔"
        PieceType.QUEEN -> "♕"
        PieceType.ROOK -> "♖"
        PieceType.BISHOP -> "♗"
        PieceType.KNIGHT -> "♘"
        PieceType.PAWN -> "♙"
    }
    PieceColor.BLACK -> when (piece.type) {
        PieceType.KING -> "♚"
        PieceType.QUEEN -> "♛"
        PieceType.ROOK -> "♜"
        PieceType.BISHOP -> "♝"
        PieceType.KNIGHT -> "♞"
        PieceType.PAWN -> "♟"
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ChessScreenPreview() {
    PlayWinTheme {
        ChessScreen(
            uiState = ChessUiState(),
            onBack = {},
            onSquareTap = {},
            onPlayAgain = {}
        )
    }
}
