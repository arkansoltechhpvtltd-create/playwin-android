package com.example.playwin.feature.game.chinesecheckers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersBoard
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersBot
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersDifficulty
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersMove
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersPosition
import com.example.playwin.core.domain.chinesecheckers.ChineseCheckersRules
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The computer never answers faster than this after your move has finished hopping into place. */
private const val COMPUTER_MIN_THINK_MILLIS = 450L

/** After the winning move lands, a moment to see it before the coupon screen. */
private const val WIN_PAUSE_MILLIS = 900L

/**
 * Chinese checkers against the computer: you play the peg colour you chose
 * ([ChineseCheckersUiState.humanColor] - Gold from the bottom point by
 * default) and move first; [ChineseCheckersBot] plays the other colour from
 * the opposite point. Which moves a peg has comes from the pure [ChineseCheckersRules];
 * every move played is sent to [GameEngineRepository.resolve], which re-checks
 * it and rules on the win - this ViewModel only tracks the selection and
 * whose turn it is. Only your win claims a reward.
 */
@HiltViewModel
class ChineseCheckersViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChineseCheckersUiState())
    val uiState: StateFlow<ChineseCheckersUiState> = _uiState.asStateFlow()

    private var computerJob: Job? = null

    /**
     * A tap on hole [hole]: onto one of the selected peg's destinations plays
     * the move; onto one of your pegs picks it up (or puts it down again);
     * anywhere else puts it down.
     */
    fun onHoleTap(hole: Int) {
        val state = _uiState.value
        if (!state.canPlay) return

        state.pendingMoves.firstOrNull { it.to == hole }?.let {
            playMove(it)
            return
        }
        if (hole == state.selected || state.position.cells.getOrNull(hole) != state.humanPlayer) {
            _uiState.update { it.copy(selected = null, pendingMoves = emptyList()) }
            return
        }
        _uiState.update { it.copy(selected = hole, pendingMoves = ChineseCheckersRules.movesFrom(state.position, hole)) }
    }

    /** Picks how strongly the computer plays; only before the first move. */
    fun onDifficultyChosen(difficulty: ChineseCheckersDifficulty) {
        if (!_uiState.value.canChooseDifficulty) return
        _uiState.update { it.copy(difficulty = difficulty) }
    }

    /** Picks your peg colour, and with it your starting point; only before the game starts. */
    fun onColorChosen(color: PegColor) {
        val state = _uiState.value
        if (state.started) return
        _uiState.value = ChineseCheckersUiState(humanColor = color, difficulty = state.difficulty)
    }

    /** Starts the game: you move first. */
    fun onStart() {
        val state = _uiState.value
        if (state.started) return
        _uiState.update { it.copy(started = true) }
        startTurn(state.position, waitMillis = 0)
    }

    fun onPlayAgain() {
        computerJob?.cancel()
        // Back to the setup, keeping the colour and difficulty the player last chose.
        val last = _uiState.value
        _uiState.value = ChineseCheckersUiState(humanColor = last.humanColor, difficulty = last.difficulty)
    }

    private fun playMove(move: ChineseCheckersMove) {
        val state = _uiState.value
        if (state.isGameOver) return
        val before = state.position
        val mover = before.sideToMove
        val after = ChineseCheckersRules.play(before, move)

        _uiState.update {
            it.copy(
                position = after,
                lastMove = move,
                moveCount = it.moveCount + 1,
                selected = null,
                pendingMoves = emptyList(),
                isResolving = true
            )
        }

        viewModelScope.launch {
            when (gameEngineRepository.resolve(GameId.CHINESE_CHECKERS, GameSnapshot.ChineseCheckers(before, move.from, move.to))) {
                GameOutcome.WIN -> finish(winner = mover, lastMove = move)

                // The other side has won: this move took the game past the
                // anti-spoiling move count with their goal already full. (Or the
                // engine rejected the move as illegal - moves only ever come from
                // the rules, so that's a guard, not a path in play.)
                GameOutcome.LOSE -> finish(winner = ChineseCheckersBoard.opponent(mover), lastMove = move)

                GameOutcome.DRAW, GameOutcome.IN_PROGRESS -> {
                    _uiState.update { it.copy(isResolving = false) }
                    startTurn(after, waitMillis = hopMillis(move))
                }
            }
        }
    }

    /** The game is over and [winner] has won; your win claims the reward once [lastMove] has landed. */
    private suspend fun finish(winner: Int, lastMove: ChineseCheckersMove) {
        _uiState.update { it.copy(winner = winner, isResolving = false) }
        if (winner == _uiState.value.humanPlayer) {
            delay(hopMillis(lastMove) + WIN_PAUSE_MILLIS)
            val coupon = rewardsRepository.claimReward(GameId.CHINESE_CHECKERS)
            couponsRepository.addCoupon(coupon)
            _uiState.update { it.copy(wonCouponId = coupon.id) }
        }
    }

    /**
     * Hands the turn over in [position]: the computer plays, or you do. A side
     * with no move at all - every peg boxed in - passes.
     */
    private fun startTurn(position: ChineseCheckersPosition, waitMillis: Long) {
        val toMove = if (ChineseCheckersRules.legalMoves(position).isEmpty()) {
            ChineseCheckersRules.pass(position).also { passed -> _uiState.update { it.copy(position = passed) } }
        } else {
            position
        }
        if (toMove.sideToMove == _uiState.value.computerPlayer) playComputerMove(waitMillis)
    }

    private fun playComputerMove(waitMillis: Long) {
        computerJob?.cancel()
        _uiState.update { it.copy(isComputerThinking = true) }
        computerJob = viewModelScope.launch {
            val state = _uiState.value
            val start = System.nanoTime()
            val move = withContext(Dispatchers.Default) { ChineseCheckersBot.chooseMove(state.position, state.difficulty) }
            delay(waitMillis + COMPUTER_MIN_THINK_MILLIS - (System.nanoTime() - start) / 1_000_000)
            _uiState.update { it.copy(isComputerThinking = false) }
            // Never null here: a computer with no move passes in startTurn before it's asked.
            move?.let(::playMove)
        }
    }

    override fun onCleared() {
        computerJob?.cancel()
    }
}
