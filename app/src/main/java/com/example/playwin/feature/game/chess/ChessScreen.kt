package com.example.playwin.feature.game.chess

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import com.example.playwin.core.designsystem.theme.PwTableSurface
import com.example.playwin.feature.game.SetupChip
import com.example.playwin.feature.game.SetupGrid
import com.example.playwin.feature.game.SetupLabel
import com.example.playwin.core.designsystem.theme.PwTableSurfaceRaised
import com.example.playwin.feature.game.GameDockButton
import com.example.playwin.feature.game.GameGoldButton
import com.example.playwin.feature.game.GameNote
import com.example.playwin.feature.game.GameStatCard
import com.example.playwin.feature.game.GameTone
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.feature.game.GameScaffold
import com.example.playwin.R
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.domain.chess.ChessEngine
import com.example.playwin.core.domain.chess.ChessPiece
import com.example.playwin.core.domain.chess.CHESS_TIME_CONTROLS
import com.example.playwin.core.domain.chess.ChessDifficulty
import com.example.playwin.core.domain.chess.TimeControl
import com.example.playwin.core.domain.chess.ChessPosition
import com.example.playwin.core.domain.chess.PieceColor
import com.example.playwin.core.domain.chess.PieceType
import com.example.playwin.core.domain.chess.Square
import com.example.playwin.feature.game.CelebrationBurst
import com.example.playwin.feature.game.chess.online.ChessOnlineRoute
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ChessRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<PlayMode?>(null) }
    // Prepares the board artwork while the mode is being picked.
    val resources = LocalContext.current.resources
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { loadBoardImage(resources) } }
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
        onPlayAgain = viewModel::onPlayAgain,
        onPromotionChosen = viewModel::onPromotionChosen,
        onPromotionCancelled = viewModel::onPromotionCancelled,
        onTimeControlChosen = viewModel::onTimeControlChosen,
        onDifficultyChosen = viewModel::onDifficultyChosen,
        onSideChosen = viewModel::onSideChosen,
        onStart = viewModel::onStart,
        onResign = viewModel::onResign
    )
}

/** The board's margin either side: about 2% of a phone's width, so the board itself spans about 96%. */
private val BoardSideMargin = 8.dp

/** The slim gold edge round the board, in place of the artwork's wide ornamental frame. */
private val BoardEdge = 3.dp

/**
 * The least height kept under the board - the pre-game pickers in full, or the
 * moves panel plus the Resign / Play again button; the board shrinks before it does.
 */
private val BelowBoardMinHeight = 216.dp

/**
 * Under the board: the pieces each side has taken, then the moves so far in
 * standard algebraic notation, numbered in pairs (1. e4 e5), scrolling to
 * keep the latest in view.
 */
@Composable
private fun MovesPanel(
    moves: List<PlayedMove>,
    capturedByYou: List<ChessPiece>,
    capturedByComputer: List<ChessPiece>,
    /** The side the player plays. */
    humanColor: PieceColor,
    modifier: Modifier = Modifier,
    /** The chosen time control while it can still be changed (before the first move); null hides the picker. */
    timeControl: TimeControl? = null,
    onTimeControlChosen: (TimeControl) -> Unit = {},
    /** The computer's difficulty, shown with the time control picker; null leaves it out. */
    difficulty: ChessDifficulty? = null,
    onDifficultyChosen: (ChessDifficulty) -> Unit = {},
    onSideChosen: (PieceColor) -> Unit = {}
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(PwTableSurfaceRaised, PwTableSurface)))
            .border(1.dp, Brush.verticalGradient(listOf(PwGoldDark.copy(alpha = 0.55f), PwGoldLight.copy(alpha = 0.15f))), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        val pairs = moves.chunked(2)
        // Before the first move nothing has been taken, so the pickers get the whole panel.
        if (timeControl == null || pairs.isNotEmpty()) {
            CapturedRow(label = "You took", pieces = capturedByYou)
            CapturedRow(label = "Computer took", pieces = capturedByComputer, modifier = Modifier.padding(top = 4.dp))
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = PwGoldDark.copy(alpha = 0.35f)
            )
        }

        val listState = rememberLazyListState()
        LaunchedEffect(pairs.size) {
            if (pairs.isNotEmpty()) listState.animateScrollToItem(pairs.lastIndex)
        }
        if (pairs.isEmpty()) {
            if (timeControl != null) {
                // The pickers scroll if the panel's too short to show them all at once.
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    // The side and the difficulty share a row, so the time control stays in view.
                    Row {
                        SidePicker(selected = humanColor, onChoose = onSideChosen)
                        if (difficulty != null) {
                            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                DifficultyPicker(selected = difficulty, onChoose = onDifficultyChosen)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TimeControlPicker(selected = timeControl, onChoose = onTimeControlChosen)
                }
            } else {
                Text(
                    text = "No moves yet - you play ${colorName(humanColor)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            SetupLabel("Moves")
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp)) {
                itemsIndexed(pairs) { index, pair ->
                    val latest = index == pairs.lastIndex
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        MoveText("${index + 1}.", latest, Modifier.width(40.dp), dim = true)
                        MoveText(pair[0].san, latest, Modifier.weight(1f))
                        MoveText(pair.getOrNull(1)?.san ?: "", latest, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Before the first move: the time controls to pick from ([CHESS_TIME_CONTROLS]), the chosen one in gold. */
@Composable
private fun TimeControlPicker(selected: TimeControl, onChoose: (TimeControl) -> Unit) {
    SetupLabel("Time per side · starts on the first move")
    // All six on one row: short labels (5m, 1h, ∞), the full one read out, and no tick.
    SetupGrid(
        options = CHESS_TIME_CONTROLS,
        label = ::shortLabel,
        selected = selected,
        onChoose = onChoose,
        ticks = false,
        columns = CHESS_TIME_CONTROLS.size,
        description = { it.label }
    )
}

/** A time control in a few characters for its narrow pill: 5m, 1h, or ∞ for no timer. */
private fun shortLabel(control: TimeControl): String {
    val seconds = control.seconds ?: return "∞"
    return if (seconds % 3600 == 0) "${seconds / 3600}h" else "${seconds / 60}m"
}

/**
 * Before the game starts: the side the player plays, as a white and a black
 * king - compact, to sit beside the difficulty. The chosen one is in gold;
 * playing Black, the computer opens.
 */
@Composable
private fun SidePicker(selected: PieceColor, onChoose: (PieceColor) -> Unit) {
    Column {
        SetupLabel("Play as")
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(PieceColor.WHITE, PieceColor.BLACK).forEach { color ->
                SetupChip(
                    label = colorName(color),
                    chosen = color == selected,
                    onClick = { onChoose(color) },
                    modifier = Modifier.width(46.dp),
                    leading = { PieceGlyph(piece = ChessPiece(PieceType.KING, color), fontSize = with(LocalDensity.current) { 24.dp.toSp() }) },
                    showLabel = false
                )
            }
        }
    }
}

private fun colorName(color: PieceColor): String = if (color == PieceColor.WHITE) "White" else "Black"

/** Before the first move: how strongly the computer plays, the chosen level in gold - no tick, to fit beside [SidePicker]. */
@Composable
private fun DifficultyPicker(selected: ChessDifficulty, onChoose: (ChessDifficulty) -> Unit) {
    SetupLabel("Computer difficulty")
    SetupGrid(options = ChessDifficulty.entries, label = { it.label }, selected = selected, onChoose = onChoose, ticks = false)
}

/** "Resign this game?" - confirming gives the game to the computer; cancelling plays on. */
@Composable
private fun ResignDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            modifier = Modifier
                .shadow(elevation = 16.dp, shape = shape)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(PickerNavyTop, PickerNavyBottom)))
                .border(2.dp, PickerGoldRim, shape)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Resign this game?",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = "The computer will win this game.",
                style = MaterialTheme.typography.bodyMedium,
                color = PwGoldDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            // Stacked, so neither choice is ever squeezed at large text sizes.
            GameGoldButton(text = "Resign", icon = Icons.Filled.Flag, onClick = onConfirm, modifier = Modifier.padding(top = 18.dp).fillMaxWidth())
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 4.dp)) {
                Text(text = "Keep playing", color = PwGoldDark, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A clock reading: m:ss, or h:mm:ss when [showHours]. Rounded up, so it reads 0:00 only once time is really out. */
private fun formatClock(millis: Long, showHours: Boolean): String {
    val total = ((millis + 999) / 1000).toInt()
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (showHours) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(total / 60, s)
}

/** Below this many seconds a clock shows red. */
private const val CLOCK_LOW_SECONDS = 20

@Composable
private fun MoveText(text: String, latest: Boolean, modifier: Modifier = Modifier, dim: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (latest && !dim) FontWeight.SemiBold else FontWeight.Normal),
        color = when {
            dim -> MaterialTheme.colorScheme.onSurfaceVariant
            latest -> PwGoldLight
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier
    )
}

/** One side's captures as small pieces, or a dash before any. */
@Composable
private fun CapturedRow(label: String, pieces: List<ChessPiece>, modifier: Modifier = Modifier) {
    val glyphSize = with(LocalDensity.current) { 18.dp.toSp() }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 112.dp).padding(end = 12.dp)
        )
        if (pieces.isEmpty()) {
            Text(text = "-", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                pieces.forEach { piece ->
                    Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        PieceGlyph(piece = piece, fontSize = glyphSize)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessScreen(
    uiState: ChessUiState,
    onBack: () -> Unit,
    onSquareTap: (Square) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
    onPromotionChosen: (PieceType) -> Unit = {},
    onPromotionCancelled: () -> Unit = {},
    onTimeControlChosen: (TimeControl) -> Unit = {},
    onDifficultyChosen: (ChessDifficulty) -> Unit = {},
    onSideChosen: (PieceColor) -> Unit = {},
    onStart: () -> Unit = {},
    onResign: () -> Unit = {}
) {
    uiState.promotion?.let { PromotionPicker(it, onChoose = onPromotionChosen, onCancel = onPromotionCancelled) }
    var askingToResign by remember { mutableStateOf(false) }
    // The question closes by itself if the game ends some other way while it's open.
    if (askingToResign && uiState.canResign) {
        ResignDialog(
            onConfirm = {
                askingToResign = false
                onResign()
            },
            onCancel = { askingToResign = false }
        )
    }
    GameScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Chess · vs Computer (${uiState.difficulty.label})", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // A narrow side margin so the board runs about 96% of the screen width;
                // the banners add their own inset to keep the usual 16dp edge.
                .padding(horizontal = BoardSideMargin, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val humanWon = uiState.humanWon
            val bannerInset = Modifier.padding(horizontal = 16.dp - BoardSideMargin)
            PlayerRow(uiState, modifier = bannerInset)
            GameNote(
                text = when {
                    uiState.isDraw && uiState.timedOut != null -> "Time's up - draw, no mating material left"
                    uiState.isDraw -> drawBannerText(uiState.drawReason)
                    uiState.resigned != null -> "You resigned - the computer wins"
                    uiState.winner != null && uiState.timedOut != null && humanWon -> "The computer ran out of time - you win! Claiming your coupon…"
                    uiState.winner != null && uiState.timedOut != null -> "You ran out of time - the computer wins"
                    humanWon -> "Checkmate - you win! Claiming your coupon…"
                    uiState.winner != null -> "Checkmate - the computer wins"
                    uiState.isResolving -> "Checking the position…"
                    uiState.awaitingStart -> "Tap Start - the computer opens as White"
                    uiState.isHumanTurn && uiState.isInCheck -> "Check! Your move - get your king to safety"
                    uiState.isInCheck -> "Check! Computer is thinking…"
                    uiState.isHumanTurn -> "Your move - you play ${colorName(uiState.humanColor)}"
                    else -> "Computer is thinking…"
                },
                tone = when {
                    humanWon -> GameTone.WIN
                    uiState.winner != null -> GameTone.BAD
                    uiState.isInCheck && !uiState.isGameOver -> GameTone.BAD
                    else -> GameTone.PLAIN
                },
                modifier = bannerInset
            )

            // The board sits right under the banner at full width; the height left
            // below it holds the captured pieces and the moves. On a short screen the
            // board gives way first, so the panel always keeps MovesPanelMinHeight.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 12.dp)
            ) {
                // Larger text needs a taller panel, so the reserve grows with the font scale.
                val belowBoard = BelowBoardMinHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)
                val boardSize = minOf(maxWidth, maxHeight - belowBoard).coerceAtLeast(0.dp)
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(boardSize)) {
                        ChessBoard(
                            position = uiState.position,
                            selectedSquare = uiState.selectedSquare,
                            legalTargets = uiState.legalTargets,
                            enabled = !uiState.isGameOver && !uiState.isResolving && uiState.isHumanTurn && !uiState.isComputerThinking,
                            onSquareTap = onSquareTap,
                            // Your own pieces always sit at the bottom.
                            flipped = uiState.humanColor == PieceColor.BLACK
                        )
                        if (humanWon) {
                            CelebrationBurst(
                                colors = listOf(PwGoldDark, PwGoldLight, MaterialTheme.colorScheme.secondary, Color.White),
                                modifier = Modifier.matchParentSize()
                            )
                        }
                    }

                    MovesPanel(
                        moves = uiState.moveHistory,
                        capturedByYou = uiState.capturedByHuman,
                        capturedByComputer = uiState.capturedByComputer,
                        humanColor = uiState.humanColor,
                        timeControl = uiState.clock.control.takeIf { uiState.canChooseTimeControl },
                        onTimeControlChosen = onTimeControlChosen,
                        difficulty = uiState.difficulty.takeIf { uiState.canChooseTimeControl },
                        onDifficultyChosen = onDifficultyChosen,
                        onSideChosen = onSideChosen,
                        modifier = bannerInset
                            .padding(top = 12.dp)
                            .weight(1f)
                    )

                    if (uiState.awaitingStart) {
                        GameGoldButton(
                            text = "Start game",
                            icon = Icons.Filled.PlayArrow,
                            onClick = onStart,
                            modifier = bannerInset.padding(top = 12.dp).fillMaxWidth()
                        )
                    } else if (uiState.isDraw || uiState.computerWon) {
                        GameGoldButton(
                            text = "Play again",
                            icon = Icons.Filled.Refresh,
                            onClick = onPlayAgain,
                            modifier = bannerInset.padding(top = 12.dp).fillMaxWidth()
                        )
                    } else if (uiState.canResign) {
                        GameDockButton(
                            label = "Resign",
                            icon = Icons.Filled.Flag,
                            onClick = { askingToResign = true },
                            modifier = bannerInset.padding(top = 12.dp).fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * The two sides as cards: the computer and you, each with its
 * clock. The side to move glows; a clock in its last [CLOCK_LOW_SECONDS]
 * seconds turns red. Without a timer the cards say which colour each side plays.
 */
@Composable
private fun PlayerRow(uiState: ChessUiState, modifier: Modifier = Modifier) {
    val clock = uiState.clock
    val showHours = (clock.control.seconds ?: 0) >= 3600
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(uiState.computerColor to "Computer", uiState.humanColor to "You").forEach { (color, name) ->
            val millis = clock.millisLeft(color)
            val toMove = !uiState.isGameOver && uiState.position.sideToMove == color
            GameStatCard(
                label = name,
                value = if (clock.control.isUnlimited) {
                    colorName(color)
                } else {
                    formatClock(millis, showHours)
                },
                icon = if (color == uiState.humanColor) Icons.Filled.Person else Icons.Filled.SmartToy,
                highlight = toMove || (color == uiState.humanColor && uiState.humanWon),
                valueColor = when {
                    !clock.control.isUnlimited && millis <= CLOCK_LOW_SECONDS * 1000L -> CheckRed
                    toMove && clock.running == color -> PwGoldDark
                    else -> null
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private val FrameDark = Color(0xFF1C1A17)
private val CheckRed = Color(0xFFD5402F)

// Pieces get their own fixed light/dark colors with a contrasting outline so
// the two sides stay obviously distinct on any square, in either app theme -
// they must never rely on the current theme's text color.
private val WhitePieceFill = Color(0xFFFFFBF0)
private val WhitePieceOutline = Color(0xFF2A2118)
private val BlackPieceFill = Color(0xFF1E1A17)
private val BlackPieceOutline = PwGoldDark

private val ChessGoldBevel = Brush.linearGradient(listOf(Color(0xFFFFEBB0), PwGoldDark, PwGoldLight, Color(0xFF6E4B10), PwGoldDark))

/** The ebony band round the squares that carries the file letters and rank numbers. */
private val LabelBand = 14.dp

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

    val boardImage = rememberBoardImage()
    val frameShape = RoundedCornerShape(10.dp)
    val labelPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = PwGoldDark.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }
    // A polished gold bevel, then a dark ebony band carrying the files and ranks in
    // gold, then the artwork cropped close around its squares (see [BoardArt]).
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(elevation = 18.dp, shape = frameShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(frameShape)
            .background(ChessGoldBevel)
            .padding(BoardEdge)
            .clip(RoundedCornerShape(7.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF26211B), FrameDark, Color(0xFF0F0D0B))))
            .drawWithContent {
                drawContent()
                // Lines the labels up with the artwork's grid, exactly as the squares below are placed.
                val band = LabelBand.toPx()
                val inner = size.width - band * 2
                val art = inner / BoardArt.CROP_WIDTH
                val cell = art * BoardArt.GRID_WIDTH / 8f
                val gridX = band + art * (BoardArt.GRID_LEFT - BoardArt.CROP_LEFT)
                val gridY = band + inner / BoardArt.CROP_HEIGHT * (BoardArt.GRID_TOP - BoardArt.CROP_TOP)
                labelPaint.textSize = band * 0.62f
                val baseline = labelPaint.textSize * 0.36f
                val canvas = drawContext.canvas.nativeCanvas
                for (i in 0..7) {
                    val file = ('a' + if (flipped) 7 - i else i).toString()
                    val rank = (if (flipped) i + 1 else 8 - i).toString()
                    canvas.drawText(file, gridX + (i + 0.5f) * cell, size.height - band / 2f + baseline, labelPaint)
                    canvas.drawText(rank, band / 2f, gridY + (i + 0.5f) * cell + baseline, labelPaint)
                }
            }
            .padding(LabelBand)
            .border(1.dp, PwGoldDark.copy(alpha = 0.55f))
    ) {
        if (boardImage != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawImage(
                    image = boardImage,
                    srcOffset = IntOffset(BoardArt.CROP_LEFT, BoardArt.CROP_TOP),
                    srcSize = IntSize(BoardArt.CROP_WIDTH, BoardArt.CROP_HEIGHT),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.High
                )
            }
        }
        // The squares are the artwork's; the play area sits exactly on its 8x8 grid.
        val artPixel = maxWidth / BoardArt.CROP_WIDTH
        BoxWithConstraints(
            modifier = Modifier
                .padding(
                    start = artPixel * (BoardArt.GRID_LEFT - BoardArt.CROP_LEFT),
                    top = maxHeight / BoardArt.CROP_HEIGHT * (BoardArt.GRID_TOP - BoardArt.CROP_TOP)
                )
                .width(artPixel * BoardArt.GRID_WIDTH)
                .aspectRatio(1f)
        ) {
            val glyphSize = with(LocalDensity.current) { (maxWidth / 8 * 0.74f).toSp() }

            // Highlights: the last move, the selected piece, where it can go, and a king in check.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = size.width / 8f
                fun topLeftOf(square: Square) =
                    Offset(displayCol(square.col.toFloat()) * cell, displayRow(square.row.toFloat()) * cell)

                fun centerOf(square: Square) = topLeftOf(square) + Offset(cell / 2f, cell / 2f)

                // Gold on the marble: a wash for the light squares, an inset rule
                // so it still reads on the black ones.
                animator.lastMove.forEach { square ->
                    drawRect(PwGoldDark.copy(alpha = 0.38f), topLeftOf(square), Size(cell, cell))
                    drawRect(
                        color = PwGoldLight.copy(alpha = 0.8f),
                        topLeft = topLeftOf(square) + Offset(cell * 0.025f, cell * 0.025f),
                        size = Size(cell * 0.95f, cell * 0.95f),
                        style = Stroke(width = cell * 0.035f)
                    )
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
                // Gold with a dark edge, so they show on cream and black marble alike.
                legalTargets.forEach { square ->
                    if (position.pieceAt(square) == null) {
                        drawCircle(Color.Black.copy(alpha = 0.45f), cell * 0.16f, centerOf(square))
                        drawCircle(PwGoldLight, cell * 0.12f, centerOf(square))
                    } else {
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.45f),
                            radius = cell * 0.43f,
                            center = centerOf(square),
                            style = Stroke(width = cell * 0.1f)
                        )
                        drawCircle(
                            color = PwGoldLight,
                            radius = cell * 0.43f,
                            center = centerOf(square),
                            style = Stroke(width = cell * 0.055f)
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
                            }
                            .drawBehind {
                                // A soft contact shadow, so the piece stands on the marble; it spreads as the piece lifts.
                                val spread = (lift - 1f) / 0.18f
                                val w = size.width * (0.62f + 0.12f * spread)
                                val h = size.height * 0.16f
                                val c = Offset(size.width / 2f, size.height * (0.86f + 0.03f * spread))
                                scale(scaleX = 1f, scaleY = h / w, pivot = c) {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            listOf(Color.Black.copy(alpha = 0.5f - 0.2f * spread), Color.Transparent),
                                            center = c,
                                            radius = w / 2f
                                        ),
                                        radius = w / 2f,
                                        center = c
                                    )
                                }
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

/**
 * Where things are in chess_board.jpg (1024px square), as [loadBoardImage] leaves it:
 * mirrored left to right, because the artwork has a light square bottom-left and
 * a1 (or h8, from Black's side) must be dark. All in the image's pixels.
 */
private object BoardArt {
    const val SIZE = 1024f

    // The outer edge of the 8x8 grid, after the mirror.
    const val GRID_LEFT = 110
    const val GRID_TOP = 116
    const val GRID_WIDTH = 801
    const val GRID_HEIGHT = 795

    /**
     * The part of the artwork the board shows: the grid and the thin dark band
     * round it (inside [labelBands], so it's evenly near-black), leaving out the
     * wide ornamental frame - the board's gold edge is drawn instead, so the
     * squares fill about 96% of the board. Drawn into a square, the grid's
     * squares come out square: 801/819 across and 795/813 down match to 0.02%.
     */
    const val CROP_LEFT = 101
    const val CROP_TOP = 107
    const val CROP_WIDTH = 920 - CROP_LEFT
    const val CROP_HEIGHT = 920 - CROP_TOP

    /**
     * The narrow band between the grid and the inner gold rule carries garbled
     * lettering; it's painted over in the band's own near-black. Left, top, right,
     * bottom, after the mirror.
     */
    val labelBands = listOf(
        AndroidRect(101, 103, 110, 921),
        AndroidRect(101, 103, 923, 116),
        AndroidRect(911, 103, 923, 921),
        AndroidRect(101, 910, 923, 921)
    )
}

/** The prepared board, built once per process by [loadBoardImage]. */
@Volatile
private var boardImageCache: ImageBitmap? = null

/** chess_board.jpg mirrored and with its label bands painted out (see [BoardArt]). Keep this off the main thread. */
private fun loadBoardImage(resources: Resources): ImageBitmap = boardImageCache ?: run {
    val source = BitmapFactory.decodeResource(resources, R.drawable.chess_board)
    val board = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(board)
    canvas.save()
    canvas.scale(-1f, 1f, source.width / 2f, 0f)
    canvas.drawBitmap(source, 0f, 0f, null)
    canvas.restore()
    val band = Paint().apply { color = FrameDark.toArgb() }
    BoardArt.labelBands.forEach { canvas.drawRect(it, band) }
    source.recycle()
    board.asImageBitmap().also { boardImageCache = it }
}

/** The prepared board; null for the frame or two before it's ready if it wasn't preloaded. */
@Composable
private fun rememberBoardImage(): ImageBitmap? {
    val resources = LocalContext.current.resources
    val image by produceState(initialValue = boardImageCache) {
        if (value == null) value = withContext(Dispatchers.Default) { loadBoardImage(resources) }
    }
    return image
}

private val PickerNavyTop = Color(0xFF1C2E56)
private val PickerNavyBottom = Color(0xFF0A1530)
private val PickerGoldRim = Brush.linearGradient(listOf(PwGoldDark, PwGoldLight, PwGoldDark))

private fun pieceName(type: PieceType): String = when (type) {
    PieceType.QUEEN -> "Queen"
    PieceType.ROOK -> "Rook"
    PieceType.BISHOP -> "Bishop"
    PieceType.KNIGHT -> "Knight"
    PieceType.PAWN -> "Pawn"
    PieceType.KING -> "King"
}

/**
 * Asks what a pawn reaching the last rank becomes: [PendingPromotion.options]
 * in order, the first (the queen) highlighted. Picking one calls [onChoose];
 * Cancel, Back or tapping outside call [onCancel] - nothing is promoted until
 * the player picks. Shared by vs-Computer and online play.
 */
@Composable
fun PromotionPicker(promotion: PendingPromotion, onChoose: (PieceType) -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            modifier = Modifier
                .shadow(elevation = 16.dp, shape = shape)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(PickerNavyTop, PickerNavyBottom)))
                .border(2.dp, PickerGoldRim, shape)
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Promote your pawn",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Text(
                text = "Choose what it becomes",
                style = MaterialTheme.typography.bodyMedium,
                color = PwGoldDark,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(
                modifier = Modifier.padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                promotion.options.forEachIndexed { index, type ->
                    PromotionOption(
                        piece = ChessPiece(type, promotion.color),
                        highlighted = index == 0,
                        onClick = { onChoose(type) }
                    )
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 10.dp)) {
                Text(text = "Cancel move", color = PwGoldDark, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PromotionOption(piece: ChessPiece, highlighted: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val density = LocalDensity.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .shadow(elevation = if (highlighted) 8.dp else 3.dp, shape = shape)
                .clip(shape)
                .background(
                    if (highlighted) {
                        Brush.verticalGradient(listOf(Color(0xFFFFF0BF), PwGoldDark))
                    } else {
                        Brush.verticalGradient(listOf(Color(0xFFFFFCF4), Color(0xFFEDE1C5)))
                    }
                )
                .border(if (highlighted) 3.dp else 1.dp, if (highlighted) PwGoldLight else PwGoldDark.copy(alpha = 0.6f), shape)
                .clickable(onClickLabel = "Promote to ${pieceName(piece.type)}", onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            PieceGlyph(piece = piece, fontSize = with(density) { 44.dp.toSp() })
        }
        Text(
            text = pieceName(piece.type),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium),
            color = if (highlighted) PwGoldDark else Color.White,
            modifier = Modifier.padding(top = 6.dp)
        )
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
    // Ivory lit from above for White; polished ebony for Black, trimmed in gold.
    Text(
        text = glyph,
        style = TextStyle(
            fontSize = fontSize,
            brush = if (isWhite) {
                Brush.verticalGradient(listOf(Color.White, WhitePieceFill, Color(0xFFE9D9B4)))
            } else {
                Brush.verticalGradient(listOf(Color(0xFF4E453C), BlackPieceFill, Color(0xFF0B0907)))
            }
        )
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
