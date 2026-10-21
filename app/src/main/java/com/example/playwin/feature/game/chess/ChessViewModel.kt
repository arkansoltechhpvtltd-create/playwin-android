package com.example.playwin.feature.game.chess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.chess.ChessBot
import com.example.playwin.core.domain.chess.ChessClock
import com.example.playwin.core.domain.chess.ChessDifficulty
import com.example.playwin.core.domain.chess.ChessEngine
import com.example.playwin.core.domain.chess.ChessNotation
import com.example.playwin.core.domain.chess.ChessMove
import com.example.playwin.core.domain.chess.PieceColor
import com.example.playwin.core.domain.chess.PieceType
import com.example.playwin.core.domain.chess.Square
import com.example.playwin.core.domain.chess.TimeControl
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.repository.RewardsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The computer never answers faster than this, so its reply doesn't land the instant yours does. */
private const val COMPUTER_MIN_THINK_MILLIS = 500L

/** On a clock, the computer spends at most 1/this of its remaining time on one move. */
private const val COMPUTER_CLOCK_FRACTION = 30

/** How often the chess clock ticks. */
private const val CLOCK_TICK_MILLIS = 100L

/**
 * Chess against the computer: the human plays White ([CHESS_HUMAN]),
 * [ChessBot] plays Black. Legal move generation and check/checkmate/
 * stalemate detection are all delegated to the pure [ChessEngine] - this
 * ViewModel only tracks selection state and whose turn it is. The *verdict*
 * on whether a checkmate actually ends the round in a win is still confirmed
 * by [GameEngineRepository.resolve], never decided directly here. Only a
 * human win claims a reward.
 */
@HiltViewModel
class ChessViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChessUiState())
    val uiState: StateFlow<ChessUiState> = _uiState.asStateFlow()

    private var computerJob: Job? = null

    fun onSquareTap(square: Square) {
        val state = _uiState.value
        if (state.isGameOver || state.isResolving || state.isComputerThinking || !state.isHumanTurn) return
        if (state.promotion != null) return

        val selected = state.selectedSquare
        if (selected == null) {
            selectIfOwnPiece(state, square)
            return
        }

        if (square == selected) {
            _uiState.update { it.copy(selectedSquare = null, pendingMoves = emptyList()) }
            return
        }

        // A pawn onto the last rank waits for the player to pick what it becomes.
        val promotion = promotionFor(state.position, state.pendingMoves, square)
        if (promotion != null) {
            _uiState.update { it.copy(promotion = promotion) }
            return
        }

        val chosenMove = state.pendingMoves.firstOrNull { it.to == square }
        if (chosenMove != null) {
            playMove(chosenMove)
            return
        }

        // Tapping a different own piece re-targets the selection instead of requiring a deselect first.
        selectIfOwnPiece(state, square)
    }

    /** Plays the held promotion as [piece]. The engine checks it's a legal move before it's played. */
    fun onPromotionChosen(piece: PieceType) {
        val state = _uiState.value
        val promotion = state.promotion ?: return
        val move = ChessEngine.legalMove(state.position, promotion.from, promotion.to, piece)
        if (move == null || !state.isHumanTurn || state.isGameOver) {
            onPromotionCancelled()
            return
        }
        _uiState.update { it.copy(promotion = null) }
        playMove(move)
    }

    /** Backs out of a promotion: nothing was played, and the pawn stays where it was. */
    fun onPromotionCancelled() {
        _uiState.update { it.copy(promotion = null, selectedSquare = null, pendingMoves = emptyList()) }
    }

    fun onPlayAgain() {
        computerJob?.cancel()
        // A new game keeps the time control and difficulty the player last chose.
        _uiState.value = ChessUiState(clock = ChessClock(_uiState.value.clock.control), difficulty = _uiState.value.difficulty)
    }

    /**
     * The player resigns: the computer's thinking stops, both clocks stop, and
     * the game engine rules on it - the resigning side loses.
     */
    fun onResign() {
        if (!_uiState.value.canResign) return
        viewModelScope.launch {
            // A move being ruled on is settled first - it may already have ended the game.
            val state = _uiState.first { !it.isResolving }
            if (!state.canResign) return@launch
            resign(state)
        }
    }

    private suspend fun resign(state: ChessUiState) {
        computerJob?.cancel()
        _uiState.update {
            it.copy(
                clock = it.clock.stopped(),
                isComputerThinking = false,
                selectedSquare = null,
                pendingMoves = emptyList(),
                promotion = null,
                isResolving = true
            )
        }
        val outcome = gameEngineRepository.resolve(
            GameId.CHESS,
            GameSnapshot.Chess(state.position, state.earlierPositions, resigned = CHESS_HUMAN)
        )
        // The engine answers for the side that moved last.
        val lastMover = state.position.sideToMove.opposite()
        val winner = when (outcome) {
            GameOutcome.WIN -> lastMover
            GameOutcome.LOSE -> lastMover.opposite()
            GameOutcome.DRAW, GameOutcome.IN_PROGRESS -> null
        }
        _uiState.update {
            it.copy(
                winner = winner?.let { color -> if (color == PieceColor.WHITE) 1 else 2 },
                isDraw = winner == null,
                resigned = CHESS_HUMAN,
                isResolving = false
            )
        }
    }

    /** Picks how strongly the computer plays; only before the first move. */
    fun onDifficultyChosen(difficulty: ChessDifficulty) {
        if (!_uiState.value.canChooseTimeControl) return
        _uiState.update { it.copy(difficulty = difficulty) }
    }

    /** Picks the time control; only before the first move. */
    fun onTimeControlChosen(control: TimeControl) {
        if (!_uiState.value.canChooseTimeControl) return
        _uiState.update { it.copy(clock = ChessClock(control)) }
    }

    init {
        // The chess clock: counts down whichever side's clock is running, on a
        // monotonic timer so it can't drift, and flags a side that runs out.
        viewModelScope.launch {
            var last = System.nanoTime()
            while (true) {
                delay(CLOCK_TICK_MILLIS)
                val now = System.nanoTime()
                val elapsed = (now - last) / 1_000_000
                last = now
                val state = _uiState.value
                if (state.isGameOver || state.clock.running == null) continue
                val clock = state.clock.tick(elapsed)
                _uiState.update { it.copy(clock = clock) }
                clock.flagged?.let { onFlag(it) }
            }
        }
    }

    /**
     * [flagged] has run out of time. The game engine rules on it - a loss,
     * or a draw if the other side couldn't mate - and the game ends there.
     */
    private fun onFlag(flagged: PieceColor) {
        computerJob?.cancel()
        val state = _uiState.value
        _uiState.update {
            it.copy(
                clock = it.clock.stopped(),
                isComputerThinking = false,
                selectedSquare = null,
                pendingMoves = emptyList(),
                promotion = null,
                isResolving = true
            )
        }
        viewModelScope.launch {
            val outcome = gameEngineRepository.resolve(
                GameId.CHESS,
                GameSnapshot.Chess(state.position, state.earlierPositions, timedOut = flagged)
            )
            // The engine answers for the side that moved last.
            val lastMover = state.position.sideToMove.opposite()
            val winner = when (outcome) {
                GameOutcome.WIN -> lastMover
                GameOutcome.LOSE -> lastMover.opposite()
                GameOutcome.DRAW, GameOutcome.IN_PROGRESS -> null
            }
            _uiState.update {
                it.copy(
                    winner = winner?.let { color -> if (color == PieceColor.WHITE) 1 else 2 },
                    isDraw = winner == null,
                    timedOut = flagged,
                    isResolving = false
                )
            }
            if (winner == CHESS_HUMAN) {
                val coupon = rewardsRepository.claimReward(GameId.CHESS)
                couponsRepository.addCoupon(coupon)
                _uiState.update { it.copy(wonCouponId = coupon.id) }
            }
        }
    }

    private fun selectIfOwnPiece(state: ChessUiState, square: Square) {
        val piece = state.position.pieceAt(square)
        if (piece == null || piece.color != state.position.sideToMove) {
            _uiState.update { it.copy(selectedSquare = null, pendingMoves = emptyList()) }
            return
        }
        val moves = ChessEngine.legalMovesFrom(state.position, square)
        _uiState.update { it.copy(selectedSquare = square, pendingMoves = moves) }
    }

    private fun playMove(move: ChessMove) {
        val state = _uiState.value
        // The game may have ended on time while this move was being chosen.
        if (state.isGameOver) return
        val position = state.position
        val mover = position.sideToMove
        val newPosition = ChessEngine.applyMove(position, move)
        val history = state.earlierPositions + ChessEngine.repetitionKey(position)
        val played = PlayedMove(ChessNotation.san(position, move), mover, ChessNotation.captured(position, move))

        _uiState.update {
            it.copy(
                position = newPosition,
                earlierPositions = history,
                moveHistory = it.moveHistory + played,
                // The mover's clock stops and the opponent's starts, the moment the move is made.
                clock = it.clock.afterMove(mover),
                selectedSquare = null,
                pendingMoves = emptyList(),
                isResolving = true
            )
        }

        viewModelScope.launch {
            when (gameEngineRepository.resolve(GameId.CHESS, GameSnapshot.Chess(newPosition, history))) {
                GameOutcome.WIN -> {
                    _uiState.update {
                        it.copy(winner = if (mover == PieceColor.WHITE) 1 else 2, clock = it.clock.stopped(), isResolving = false)
                    }
                    if (mover == CHESS_HUMAN) {
                        val coupon = rewardsRepository.claimReward(GameId.CHESS)
                        couponsRepository.addCoupon(coupon)
                        _uiState.update { it.copy(wonCouponId = coupon.id) }
                    }
                }

                GameOutcome.DRAW -> _uiState.update {
                    it.copy(
                        isDraw = true,
                        drawReason = ChessEngine.gameStatus(newPosition, history),
                        clock = it.clock.stopped(),
                        isResolving = false
                    )
                }

                GameOutcome.LOSE, GameOutcome.IN_PROGRESS -> {
                    _uiState.update { it.copy(isResolving = false) }
                    if (newPosition.sideToMove != CHESS_HUMAN) playComputerMove()
                }
            }
        }
    }

    private fun playComputerMove() {
        computerJob?.cancel()
        _uiState.update { it.copy(isComputerThinking = true) }
        computerJob = viewModelScope.launch {
            val state = _uiState.value
            val start = System.nanoTime()
            val budget = if (state.clock.control.isUnlimited) {
                state.difficulty.timeBudgetMillis
            } else {
                minOf(state.difficulty.timeBudgetMillis, state.clock.millisLeft(state.position.sideToMove) / COMPUTER_CLOCK_FRACTION)
            }
            val move = withContext(Dispatchers.Default) {
                ChessBot.chooseMove(state.position, state.difficulty, state.earlierPositions, timeBudgetMillis = budget)
            }
            delay(COMPUTER_MIN_THINK_MILLIS - (System.nanoTime() - start) / 1_000_000)
            _uiState.update { it.copy(isComputerThinking = false) }
            move?.let(::playMove)
        }
    }

    override fun onCleared() {
        computerJob?.cancel()
    }
}
