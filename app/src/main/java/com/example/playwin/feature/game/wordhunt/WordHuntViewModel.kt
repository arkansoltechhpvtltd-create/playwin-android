package com.example.playwin.feature.game.wordhunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.repository.RewardsRepository
import com.example.playwin.core.domain.wordhunt.WORD_HUNT_TURN_SECONDS
import com.example.playwin.core.domain.wordhunt.WordHuntBot
import com.example.playwin.core.domain.wordhunt.WordHuntClaim
import com.example.playwin.core.domain.wordhunt.WordHuntScoring
import com.example.playwin.core.domain.wordhunt.matches
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Head-to-head Word Hunt against the computer on one shared grid. Players
 * alternate turns; on a turn you have [WORD_HUNT_TURN_SECONDS] to find any
 * unclaimed word (tap its first and last letter), scoring its length plus a
 * speed bonus. [WordHuntBot] plays the computer's turns. Whether the game is
 * decided - and who won - is always confirmed by [GameEngineRepository.resolve]
 * from the claims, never decided here. Only a human win claims a reward.
 */
@HiltViewModel
class WordHuntViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordHuntUiState())
    val uiState: StateFlow<WordHuntUiState> = _uiState.asStateFlow()

    private var turnJob: Job? = null

    init {
        startTurn(WordHuntSide.HUMAN)
    }

    fun onCellTap(row: Int, col: Int) {
        val state = _uiState.value
        if (!state.canHunt) return

        val first = state.firstSelection
        val second = row to col
        if (first == null || first == second) {
            _uiState.update { it.copy(firstSelection = if (first == null) second else null) }
            return
        }

        val claimed = (state.humanClaims + state.computerClaims).map { it.word }.toSet()
        val match = state.words.firstOrNull { it.word !in claimed && it.matches(first, second) }
        _uiState.update { it.copy(firstSelection = null) }
        if (match == null) return

        turnJob?.cancel()
        claim(WordHuntSide.HUMAN, WordHuntClaim(match.word, WordHuntScoring.bonusFor(state.secondsLeft)))
    }

    fun onPlayAgain() {
        turnJob?.cancel()
        _uiState.value = WordHuntUiState()
        startTurn(WordHuntSide.HUMAN)
    }

    private fun claim(side: WordHuntSide, claim: WordHuntClaim) {
        _uiState.update {
            when (side) {
                WordHuntSide.HUMAN -> it.copy(humanClaims = it.humanClaims + claim, isResolving = true)
                WordHuntSide.COMPUTER -> it.copy(computerClaims = it.computerClaims + claim, isResolving = true)
            }
        }

        viewModelScope.launch {
            val state = _uiState.value
            val outcome = gameEngineRepository.resolve(
                GameId.WORD_HUNT,
                GameSnapshot.WordHunt(
                    puzzleWords = state.words.map { it.word },
                    myClaims = state.humanClaims,
                    opponentClaims = state.computerClaims
                )
            )
            if (outcome == GameOutcome.IN_PROGRESS) {
                _uiState.update { it.copy(isResolving = false, lastTurnTimedOut = false) }
                startTurn(if (side == WordHuntSide.HUMAN) WordHuntSide.COMPUTER else WordHuntSide.HUMAN)
                return@launch
            }
            finish(outcome)
        }
    }

    /** The game clock ran out: the game ends on the scores as they stand, as the engine rules them. */
    private fun endOnTime() {
        turnJob?.cancel()
        _uiState.update { it.copy(isResolving = true, firstSelection = null) }
        viewModelScope.launch {
            val state = _uiState.value
            val outcome = gameEngineRepository.resolve(
                GameId.WORD_HUNT,
                GameSnapshot.WordHunt(
                    puzzleWords = state.words.map { it.word },
                    myClaims = state.humanClaims,
                    opponentClaims = state.computerClaims,
                    timeUp = true
                )
            )
            _uiState.update { it.copy(endedOnTime = true) }
            finish(outcome)
        }
    }

    private suspend fun finish(outcome: GameOutcome) {
        _uiState.update { it.copy(result = outcome, isResolving = false) }
        if (outcome == GameOutcome.WIN) {
            val coupon = rewardsRepository.claimReward(GameId.WORD_HUNT)
            couponsRepository.addCoupon(coupon)
            _uiState.update { it.copy(wonCouponId = coupon.id) }
        }
    }

    /**
     * Runs [side]'s turn clock - and the game clock with it; the computer claims
     * its word partway through, the human by tapping.
     */
    private fun startTurn(side: WordHuntSide) {
        turnJob?.cancel()
        _uiState.update { it.copy(turn = side, secondsLeft = WORD_HUNT_TURN_SECONDS, firstSelection = null) }
        val computerFindsAt = if (side == WordHuntSide.COMPUTER) WORD_HUNT_TURN_SECONDS - WordHuntBot.thinkSeconds() else null

        turnJob = viewModelScope.launch {
            for (secondsLeft in WORD_HUNT_TURN_SECONDS downTo 0) {
                _uiState.update { it.copy(secondsLeft = secondsLeft) }
                if (secondsLeft == computerFindsAt) {
                    val state = _uiState.value
                    val claimed = (state.humanClaims + state.computerClaims).map { it.word }.toSet()
                    val word = WordHuntBot.chooseWord(state.grid, state.words.map { it.word }, claimed)
                    if (word != null) {
                        claim(WordHuntSide.COMPUTER, WordHuntClaim(word.word, WordHuntScoring.bonusFor(secondsLeft)))
                        return@launch
                    }
                }
                delay(1000)
                _uiState.update { it.copy(gameSecondsLeft = (it.gameSecondsLeft - 1).coerceAtLeast(0)) }
                if (_uiState.value.gameSecondsLeft == 0) {
                    endOnTime()
                    return@launch
                }
            }
            // Time's up: the turn passes without a word.
            _uiState.update { it.copy(lastTurnTimedOut = true) }
            startTurn(if (side == WordHuntSide.HUMAN) WordHuntSide.COMPUTER else WordHuntSide.HUMAN)
        }
    }

    override fun onCleared() {
        turnJob?.cancel()
    }
}
