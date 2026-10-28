package com.example.playwin.feature.game.snakesandladders

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.Refresh
import com.example.playwin.feature.game.GameGoldButton
import com.example.playwin.feature.game.GameNote
import com.example.playwin.feature.game.GameTone
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwGoldDark
import com.example.playwin.core.designsystem.theme.PwGoldLight
import com.example.playwin.core.designsystem.theme.PwTableSurface
import com.example.playwin.core.designsystem.theme.PwTableSurfaceRaised
import com.example.playwin.core.designsystem.theme.PwTableTextMuted
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersMove
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersRules
import com.example.playwin.feature.game.GameScaffold
import com.example.playwin.feature.game.FirstTurn
import com.example.playwin.feature.game.GameSetupOverlay
import com.example.playwin.feature.game.SetupNote
import com.example.playwin.feature.game.SetupPicker
import com.example.playwin.feature.game.online.GameModeSelectScreen
import com.example.playwin.feature.game.online.PlayMode
import com.example.playwin.feature.game.snakesandladders.online.SnakesAndLaddersOnlineRoute

/** Entry point for Snakes & Ladders: play the computer, or get paired with a random online player. */
@Composable
fun SnakesAndLaddersRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    var mode by remember { mutableStateOf<PlayMode?>(null) }
    when (mode) {
        null -> GameModeSelectScreen(title = "Snakes & Ladders", onBack = onBack, onSelectMode = { mode = it })
        PlayMode.COMPUTER -> SnakesAndLaddersComputerRoute(onBack = { mode = null }, onWin = onWin)
        PlayMode.ONLINE -> SnakesAndLaddersOnlineRoute(
            onBack = { mode = null },
            onWin = onWin,
            onPlayComputer = { mode = PlayMode.COMPUTER }
        )
    }
}

@Composable
private fun SnakesAndLaddersComputerRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: SnakesAndLaddersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    SnakesAndLaddersScreen(
        uiState = uiState,
        onBack = onBack,
        onRoll = viewModel::onRoll,
        onPlayAgain = viewModel::onPlayAgain,
        onColorChosen = viewModel::onColorChosen,
        onFirstTurnChosen = viewModel::onFirstTurnChosen,
        onStart = viewModel::onStart
    )
}

@Composable
fun SnakesAndLaddersScreen(
    uiState: SnakesAndLaddersUiState,
    onBack: () -> Unit,
    onRoll: () -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
    onColorChosen: (SlTokenColor) -> Unit = {},
    onFirstTurnChosen: (FirstTurn) -> Unit = {},
    onStart: () -> Unit = {}
) {
    val names = listOf("You", "Computer")
    val tokenColors = uiState.tokenColors
    val move = uiState.lastMove
    val winner = uiState.winner
    GameScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Snakes & Ladders · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SnakesAndLaddersPlayers(
                names = names,
                positions = uiState.game.positions,
                tokenColors = tokenColors,
                // While a roll plays out, the player making it stays highlighted.
                turn = (if (uiState.isBusy) move?.player ?: uiState.game.turn else uiState.game.turn).takeIf { winner == null },
                winner = winner
            )
            when {
                !uiState.started -> SnakesAndLaddersNote("Choose your token and who rolls first", NoteTone.PLAIN)
                winner == SL_HUMAN -> SnakesAndLaddersNote("You win! Claiming your coupon…", NoteTone.WIN)
                winner != null -> SnakesAndLaddersNote("The computer wins this round", NoteTone.PLAIN)
                else -> SnakesAndLaddersNote(
                    text = moveNote(move, move?.let { names[it.player] }.orEmpty(), rollsAgain = move != null && uiState.game.turn == move.player)
                        ?: "Roll the die to set off from the start",
                    tone = noteTone(move)
                )
            }

            // The board takes all the height between the players and the dock.
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                SnakesAndLaddersBoard(
                    positions = uiState.game.positions,
                    lastMove = move,
                    tokenColors = tokenColors,
                    description = boardDescription(names, uiState.game.positions),
                    activePlayer = uiState.game.turn.takeIf { uiState.started && winner == null && !uiState.isBusy },
                    celebrate = winner == SL_HUMAN
                )
                if (!uiState.started) {
                    GameSetupOverlay(onStart = onStart) {
                        SetupPicker(
                            title = "Play as",
                            options = SlTokenColor.entries,
                            label = { it.label },
                            selected = uiState.humanColor,
                            onChoose = onColorChosen,
                            columns = 2,
                            swatch = { it.colors[1] }
                        )
                        SetupPicker(
                            title = "Who rolls first",
                            options = FirstTurn.entries,
                            label = { it.label },
                            selected = uiState.firstTurn,
                            onChoose = onFirstTurnChosen,
                            columns = 2
                        )
                        SetupNote(
                            "The computer plays ${uiState.computerColor.label} · " +
                                if (uiState.firstTurn == FirstTurn.YOU) "you roll first" else "the computer rolls first"
                        )
                    }
                }
            }

            SnakesAndLaddersDock(
                dieValue = move?.roll,
                rollCount = uiState.game.turnsPlayed,
                canRoll = uiState.canRoll,
                waitingLabel = when {
                    !uiState.started -> "Pick your token"
                    winner == SL_HUMAN -> "Well played!"
                    uiState.isBusy && move?.player == SL_HUMAN -> "Moving…"
                    uiState.isBusy -> "Computer is moving…"
                    else -> "Computer is rolling…"
                },
                onRoll = onRoll,
                action = if (winner == SL_COMPUTER) "Play again" to onPlayAgain else null,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

/** How the line under the players is tinted: good news in emerald, a snake in red, a win in gold. */
enum class NoteTone { PLAIN, GOOD, BAD, WIN }

fun noteTone(move: SnakesAndLaddersMove?): NoteTone = when {
    move == null -> NoteTone.PLAIN
    move.climbedLadder -> NoteTone.GOOD
    move.hitSnake || move.forfeited -> NoteTone.BAD
    else -> NoteTone.PLAIN
}

/** The one line of commentary between the players and the board: what the last roll did. The same line every game uses. */
@Composable
fun SnakesAndLaddersNote(text: String, tone: NoteTone, modifier: Modifier = Modifier) {
    GameNote(
        text = text,
        tone = when (tone) {
            NoteTone.PLAIN -> GameTone.PLAIN
            NoteTone.GOOD -> GameTone.GOOD
            NoteTone.BAD -> GameTone.BAD
            NoteTone.WIN -> GameTone.WIN
        },
        modifier = modifier
    )
}

/**
 * One card per player side by side: their pawn, name and square, and a gold
 * bar of how far round the board they are. The card whose roll it is ([turn])
 * is outlined in glowing gold and tagged; at the end the [winner]'s is crowned.
 */
@Composable
fun SnakesAndLaddersPlayers(
    names: List<String>,
    positions: List<Int>,
    tokenColors: List<List<Color>>,
    turn: Int?,
    winner: Int?,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        names.forEachIndexed { player, name ->
            PlayerCard(
                name = name,
                square = positions[player],
                colors = tokenColors[player],
                active = player == turn,
                winner = player == winner,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlayerCard(
    name: String,
    square: Int,
    colors: List<Color>,
    active: Boolean,
    winner: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val highlight by animateFloatAsState(if (active || winner) 1f else 0f, tween(300), label = "slCardHighlight")
    val progress by animateFloatAsState(square / SnakesAndLaddersRules.FINISH.toFloat(), tween(500), label = "slCardProgress")
    Box(
        modifier = modifier
            .shadow(
                elevation = (4 + 8 * highlight).dp,
                shape = shape,
                ambientColor = PwGoldDark.copy(alpha = highlight),
                spotColor = PwGoldDark.copy(alpha = highlight)
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(PwTableSurfaceRaised, PwTableSurface)))
            .background(Brush.horizontalGradient(listOf(PwGoldDark.copy(alpha = 0.14f * highlight), Color.Transparent)))
            .border(
                width = (1f + 0.5f * highlight).dp,
                brush = Brush.linearGradient(
                    listOf(
                        PwGoldDark.copy(alpha = 0.25f + 0.75f * highlight),
                        PwGoldLight.copy(alpha = 0.2f + 0.6f * highlight)
                    )
                ),
                shape = shape
            )
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(width = 30.dp, height = 38.dp)) {
                val r = size.height / 2.5f
                drawPawn(Offset(size.width / 2f, size.height / 2f + r * 0.12f), r, colors, lift = 0f, glow = null)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                        color = if (active || winner) PwGoldDark else PwTableTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (winner) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = "Winner",
                            tint = PwGoldDark,
                            modifier = Modifier.padding(start = 4.dp).size(16.dp)
                        )
                    } else if (active) {
                        TurnDot(modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    if (square <= SnakesAndLaddersRules.START) {
                        Text(
                            text = "Start",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    } else {
                        Text(
                            text = "$square",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = " / ${SnakesAndLaddersRules.FINISH}",
                            style = MaterialTheme.typography.labelMedium,
                            color = PwTableTextMuted,
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }
        }
        // How far round the board, as a thin gold bar along the bottom edge.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(Brush.horizontalGradient(listOf(PwGoldLight, PwGoldDark)))
            )
        }
    }
}

/** A small gold dot that breathes beside the name of the player whose roll it is. */
@Composable
private fun TurnDot(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "slTurnDot").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "slTurnDotPulse"
    )
    Canvas(modifier = modifier.size(10.dp)) {
        drawCircle(PwGoldDark.copy(alpha = 0.3f * pulse), size.minDimension / 2f)
        drawCircle(PwGoldDark.copy(alpha = 0.6f + 0.4f * pulse), size.minDimension / 4f)
    }
}

/**
 * The control dock under the board: the die and, beside it, a gold Roll
 * button that shimmers while [canRoll], or a quiet [waitingLabel] while it
 * isn't. An [action] (label to handler) - Play again - takes the button's place.
 */
@Composable
fun SnakesAndLaddersDock(
    dieValue: Int?,
    rollCount: Int,
    canRoll: Boolean,
    waitingLabel: String,
    onRoll: () -> Unit,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null
) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(PwTableSurfaceRaised, PwTableSurface)))
            .border(1.dp, Brush.verticalGradient(listOf(PwGoldDark.copy(alpha = 0.55f), PwGoldLight.copy(alpha = 0.15f))), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Keyed by the roll count so two equal rolls in a row still throw.
        key(rollCount) {
            SnakesAndLaddersDie(value = dieValue, enabled = canRoll && action == null, onClick = onRoll)
        }
        Spacer(modifier = Modifier.width(14.dp))
        when {
            action != null -> GameGoldButton(text = action.first, icon = Icons.Filled.Refresh, onClick = action.second, modifier = Modifier.weight(1f))
            canRoll -> GameGoldButton(text = "Roll", onClick = onRoll, modifier = Modifier.weight(1f))
            else -> Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black.copy(alpha = 0.22f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = waitingLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = PwTableTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun squareLabel(square: Int): String =
    if (square <= SnakesAndLaddersRules.START) "start" else "square $square"

fun boardDescription(names: List<String>, positions: List<Int>): String =
    "Snakes and Ladders board. " + names.indices.joinToString(", ") { "${names[it]} on ${squareLabel(positions[it])}" }

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SnakesAndLaddersScreenPreview() {
    PlayWinTheme {
        SnakesAndLaddersScreen(
            uiState = SnakesAndLaddersUiState(),
            onBack = {},
            onRoll = {},
            onPlayAgain = {}
        )
    }
}
