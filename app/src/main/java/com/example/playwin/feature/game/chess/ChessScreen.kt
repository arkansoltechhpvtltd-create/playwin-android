package com.example.playwin.feature.game.chess

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.domain.chess.ChessEngine
import com.example.playwin.core.domain.chess.ChessPiece
import com.example.playwin.core.domain.chess.ChessPosition
import com.example.playwin.core.domain.chess.PieceColor
import com.example.playwin.core.domain.chess.PieceType
import com.example.playwin.core.domain.chess.Square
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.GameStatusBanner
import com.example.playwin.feature.game.chess.online.ChessOnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ChessRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<PlayMode?>(null) }
    when (mode) {
        null -> GameModeSelectScreen(title = "Chess", onBack = onBack, onSelectMode = { mode = it })
        PlayMode.COMPUTER -> ChessComputerRoute(onBack = { mode = null }, onWin = onWin)
        PlayMode.ONLINE -> ChessOnlineRoute(
            onBack = { mode = null },
            onWin = onWin,
            onPlayComputer = { mode = PlayMode.COMPUTER }
        )
    }
}

@Composable
private fun ChessComputerRoute(
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
        topBar = { PwTopBar(title = "Chess · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val humanWon = uiState.winner == 1
            when {
                uiState.isDraw -> StatusBanner(text = "Stalemate - it's a draw!")
                uiState.winner != null -> StatusBanner(
                    text = if (humanWon) "Checkmate - you win! Claiming your coupon…" else "Checkmate - the computer wins",
                    trophy = humanWon
                )
                else -> TurnBanner(uiState)
            }

            Box(modifier = Modifier.padding(top = 16.dp)) {
                ChessBoard(
                    position = uiState.position,
                    selectedSquare = uiState.selectedSquare,
                    legalTargets = uiState.legalTargets,
                    enabled = !uiState.isGameOver && !uiState.isResolving && uiState.isHumanTurn && !uiState.isComputerThinking,
                    onSquareTap = onSquareTap
                )
                if (humanWon) {
                    CelebrationBurst(
                        colors = listOf(PwGoldDark, PwGoldLight, MaterialTheme.colorScheme.secondary, Color.White),
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            if (uiState.isDraw || uiState.winner == 2) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

@Composable
private fun TurnBanner(uiState: ChessUiState, modifier: Modifier = Modifier) {
    val accent = if (uiState.isInCheck) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    GameStatusBanner(accent = accent, modifier = modifier) {
        Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            PieceGlyph(
                piece = ChessPiece(PieceType.KING, uiState.position.sideToMove),
                fontSize = with(density) { 24.dp.toSp() }
            )
        }
        Text(
            text = (if (uiState.isHumanTurn) "Your move (White)" else "Computer is thinking…") + if (uiState.isInCheck) " - check!" else "",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (uiState.isInCheck) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        )
        if (uiState.isResolving || uiState.isComputerThinking) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun StatusBanner(text: String, modifier: Modifier = Modifier, trophy: Boolean = false) {
    val gold = MaterialTheme.colorScheme.primary
    GameStatusBanner(accent = gold, modifier = modifier) {
        if (trophy) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = gold,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(26.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

private val LightSquare = Color(0xFFF1E4C6)
private val LightSquareShade = Color(0xFFE4D3AE)
private val DarkSquare = Color(0xFF3B7A64)
private val DarkSquareShade = Color(0xFF2C6150)
private val FrameLight = Color(0xFF5E3A20)
private val FrameDark = Color(0xFF2E170A)
private val CheckRed = Color(0xFFD5402F)

// Pieces get their own fixed light/dark colors with a contrasting outline so
// the two sides stay obviously distinct on any square, in either app theme -
// they must never rely on the current theme's text color.
private val WhitePieceFill = Color(0xFFFFFBF0)
private val WhitePieceOutline = Color(0xFF2A2118)
private val BlackPieceFill = Color(0xFF1E1A17)
private val BlackPieceOutline = Color(0xFFEADFC6)

private const val MOVE_MILLIS = 240
private const val CAPTURE_MILLIS = 220

/** One piece on screen. It keeps its identity across moves so it can slide rather than jump. */
private class PieceSprite(val id: Int, piece: ChessPiece, var square: Square) {
    var piece by mutableStateOf(piece)

    /** Board coordinates (col, row) of where it's drawn right now. */
    val at = Animatable(Offset(square.col.toFloat(), square.row.toFloat()), Offset.VectorConverter)

    /** 1 while on the board; runs to 0 as a captured piece fades and shrinks away. */
    val presence = Animatable(1f)
    var captured = false
}

/**
 * Turns successive [ChessPosition]s - which only say what is on each square -
 * into moving pieces, by pairing every square a piece left with the square the
 * same piece (or, for a promotion, the same side) arrived on. A piece that left
 * with nowhere to go was captured. Display only: it never decides a move.
 */
private class ChessBoardAnimator(initial: ChessPosition) {
    val sprites = mutableStateListOf<PieceSprite>()
    var lastMove by mutableStateOf<List<Square>>(emptyList())
        private set
    private var shown = initial
    private var nextId = 0

    init {
        placeAll(initial)
    }

    private fun placeAll(position: ChessPosition) {
        sprites.clear()
        for (row in 0..7) {
            for (col in 0..7) {
                position.board[row][col]?.let { sprites += PieceSprite(nextId++, it, Square(row, col)) }
            }
        }
    }

    fun show(position: ChessPosition, scope: CoroutineScope) {
        val before = shown
        if (before.board == position.board) return
        shown = position

        val left = mutableListOf<Square>()
        val arrived = mutableListOf<Square>()
        for (row in 0..7) {
            for (col in 0..7) {
                val old = before.board[row][col]
                val new = position.board[row][col]
                if (old != new) {
                    if (old != null) left += Square(row, col)
                    if (new != null) arrived += Square(row, col)
                }
            }
        }
        // More than a move's worth of change means a new game: just set the board up.
        if (left.size + arrived.size > 6) {
            placeAll(position)
            lastMove = emptyList()
            return
        }

        val spriteOn = sprites.filterNot { it.captured }.associateBy { it.square }
        val moved = mutableListOf<Square>()
        for (target in arrived) {
            val piece = position.pieceAt(target) ?: continue
            val source = left.firstOrNull { before.pieceAt(it) == piece }
                ?: left.firstOrNull { before.pieceAt(it)?.color == piece.color }
            val sprite = source?.let { spriteOn[it] }
            if (source == null || sprite == null) {
                sprites += PieceSprite(nextId++, piece, target)
                continue
            }
            left -= source
            moved += source
            moved += target
            sprite.square = target
            sprite.piece = piece
            scope.launch {
                sprite.at.animateTo(
                    Offset(target.col.toFloat(), target.row.toFloat()),
                    tween(MOVE_MILLIS, easing = FastOutSlowInEasing)
                )
            }
        }
        for (source in left) {
            val sprite = spriteOn[source] ?: continue
            sprite.captured = true
            scope.launch {
                sprite.presence.animateTo(0f, tween(CAPTURE_MILLIS))
                sprites -= sprite
            }
        }
        lastMove = moved
    }
}

/**
 * The board, from White's side by default; [flipped] shows it from Black's
 * side (Black's pieces at the bottom), for the Black player online.
 */
@Composable
fun ChessBoard(
    position: ChessPosition,
    selectedSquare: Square?,
    legalTargets: List<Square>,
    enabled: Boolean,
    onSquareTap: (Square) -> Unit,
    modifier: Modifier = Modifier,
    flipped: Boolean = false
) {
    val animator = remember { ChessBoardAnimator(position) }
    val scope = rememberCoroutineScope()
    SideEffect { animator.show(position, scope) }

    // Where the king under attack stands, for the warning glow.
    val checkedKing = remember(position) {
        if (!ChessEngine.isInCheck(position, position.sideToMove)) {
            null
        } else {
            (0..7).firstNotNullOfOrNull { row ->
                (0..7).firstOrNull { col ->
                    position.board[row][col] == ChessPiece(PieceType.KING, position.sideToMove)
                }?.let { Square(row, it) }
            }
        }
    }

    fun displayCol(col: Float) = if (flipped) 7f - col else col
    fun displayRow(row: Float) = if (flipped) row else 7f - row

    val frameShape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(elevation = 12.dp, shape = frameShape)
            .clip(frameShape)
            .background(Brush.linearGradient(listOf(FrameLight, FrameDark, FrameLight)))
            .border(1.5.dp, Brush.linearGradient(listOf(PwGoldDark, PwGoldLight, PwGoldDark)), frameShape)
            .padding(10.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
        ) {
            val glyphSize = with(LocalDensity.current) { (maxWidth / 8 * 0.74f).toSp() }

            // The squares never change, so they keep their own layer.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()
            ) {
                val cell = size.width / 8f
                for (row in 0..7) {
                    for (col in 0..7) {
                        val dark = (row + col) % 2 == 0
                        val topLeft = Offset(displayCol(col.toFloat()) * cell, displayRow(row.toFloat()) * cell)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = if (dark) listOf(DarkSquare, DarkSquareShade) else listOf(LightSquare, LightSquareShade),
                                start = topLeft,
                                end = topLeft + Offset(cell, cell)
                            ),
                            topLeft = topLeft,
                            size = Size(cell, cell)
                        )
                    }
                }
            }

            // Highlights: the last move, the selected piece, where it can go, and a king in check.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = size.width / 8f
                fun topLeftOf(square: Square) =
                    Offset(displayCol(square.col.toFloat()) * cell, displayRow(square.row.toFloat()) * cell)

                fun centerOf(square: Square) = topLeftOf(square) + Offset(cell / 2f, cell / 2f)

                animator.lastMove.forEach { square ->
                    drawRect(PwGoldDark.copy(alpha = 0.38f), topLeftOf(square), Size(cell, cell))
                }
                checkedKing?.let { square ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(CheckRed.copy(alpha = 0.9f), CheckRed.copy(alpha = 0f)),
                            center = centerOf(square),
                            radius = cell * 0.62f
                        ),
                        radius = cell * 0.62f,
                        center = centerOf(square)
                    )
                }
                selectedSquare?.let { square ->
                    drawRect(PwGoldDark.copy(alpha = 0.62f), topLeftOf(square), Size(cell, cell))
                    drawRect(
                        color = PwGoldLight,
                        topLeft = topLeftOf(square) + Offset(cell * 0.03f, cell * 0.03f),
                        size = Size(cell * 0.94f, cell * 0.94f),
                        style = Stroke(width = cell * 0.06f)
                    )
                }
                legalTargets.forEach { square ->
                    if (position.pieceAt(square) == null) {
                        drawCircle(Color.Black.copy(alpha = 0.28f), cell * 0.15f, centerOf(square))
                    } else {
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.32f),
                            radius = cell * 0.43f,
                            center = centerOf(square),
                            style = Stroke(width = cell * 0.08f)
                        )
                    }
                }
            }

            // Tap targets, one per square.
            Column(modifier = Modifier.fillMaxSize()) {
                for (row in if (flipped) 0..7 else 7 downTo 0) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (col in if (flipped) 7 downTo 0 else 0..7) {
                            val square = Square(row, col)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        enabled = enabled,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onSquareTap(square) }
                            )
                        }
                    }
                }
            }

            // The pieces, each sliding between squares; the selected one lifts.
            animator.sprites.forEach { sprite ->
                key(sprite.id) {
                    val lift by animateFloatAsState(
                        targetValue = if (!sprite.captured && sprite.square == selectedSquare) 1.18f else 1f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
                        label = "chessPieceLift"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.125f)
                            .graphicsLayer {
                                val at = sprite.at.value
                                val presence = sprite.presence.value
                                translationX = displayCol(at.x) * size.width
                                translationY = displayRow(at.y) * size.height
                                scaleX = lift * (0.5f + 0.5f * presence)
                                scaleY = lift * (0.5f + 0.5f * presence)
                                alpha = presence
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        PieceGlyph(piece = sprite.piece, fontSize = glyphSize)
                    }
                }
            }
        }
    }
}

/** A solid piece silhouette with a contrasting outline and a soft drop shadow. */
@Composable
private fun PieceGlyph(piece: ChessPiece, fontSize: TextUnit) {
    val isWhite = piece.color == PieceColor.WHITE
    val glyph = glyphFor(piece.type)
    val outlineWidth = with(LocalDensity.current) { fontSize.toPx() * 0.09f }
    Text(
        text = glyph,
        style = TextStyle(
            fontSize = fontSize,
            color = if (isWhite) WhitePieceOutline else BlackPieceOutline,
            drawStyle = Stroke(width = outlineWidth, join = StrokeJoin.Round),
            shadow = Shadow(color = Color.Black.copy(alpha = 0.45f), offset = Offset(0f, outlineWidth), blurRadius = outlineWidth * 2f)
        )
    )
    Text(
        text = glyph,
        style = TextStyle(fontSize = fontSize, color = if (isWhite) WhitePieceFill else BlackPieceFill)
    )
}

/**
 * Both sides use the solid glyph set and are told apart by colour alone. The
 * trailing variation selector asks for the text form, so the pawn is never
 * swapped for its emoji.
 */
private fun glyphFor(type: PieceType): String = when (type) {
    PieceType.KING -> "♚"
    PieceType.QUEEN -> "♛"
    PieceType.ROOK -> "♜"
    PieceType.BISHOP -> "♝"
    PieceType.KNIGHT -> "♞"
    PieceType.PAWN -> "♟"
} + "︎"

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
