package com.example.playwin.feature.game.bingo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.bingo.BingoBot
import com.example.playwin.core.domain.bingo.BingoRules
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.repository.RewardsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val COMPUTER_THINK_MILLIS = 900L

/**
 * Head-to-head bingo against the computer: each side has its own card, the
 * two take turns calling a number (which marks both cards), and the first
 * completed line wins. [BingoBot] picks the computer's calls. Whether a call
 * completed a line is always confirmed by [GameEngineRepository.resolve],
 * never decided directly here. Only a human win claims a reward.
 */
@HiltViewModel
class BingoViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BingoUiState())
    val uiState: StateFlow<BingoUiState> = _uiState.asStateFlow()

    private var computerJob: Job? = null

    /** The human calls [number] - it must be on their card and not yet called. */
    fun onCallNumber(number: Int) {
        val state = _uiState.value
        if (!state.canCall || number in state.called || state.myCard.none { number in it }) return
        call(number, BingoSide.HUMAN)
    }

    fun onCallRandom() {
        val state = _uiState.value
        if (!state.canCall) return
        BingoRules.randomUncalled(state.called)?.let { call(it, BingoSide.HUMAN) }
    }

    fun onPlayAgain() {
        computerJob?.cancel()
        _uiState.value = BingoUiState()
    }

    private fun call(number: Int, caller: BingoSide) {
        val called = _uiState.value.called + number
        _uiState.update { it.copy(called = called, lastCall = number, lastCaller = caller, isResolving = true) }

        viewModelScope.launch {
            val state = _uiState.value
            val (callerCard, otherCard) = when (caller) {
                BingoSide.HUMAN -> state.myCard to state.computerCard
                BingoSide.COMPUTER -> state.computerCard to state.myCard
            }
            val other = if (caller == BingoSide.HUMAN) BingoSide.COMPUTER else BingoSide.HUMAN
            val winner = when (gameEngineRepository.resolveBingoCall(callerCard, otherCard, called)) {
                BingoCallResult.CALLER_WINS -> caller
                BingoCallResult.OTHER_WINS -> other
                BingoCallResult.NO_WINNER -> null
            }

            if (winner != null) {
                _uiState.update { it.copy(winner = winner, isResolving = false) }
                if (winner == BingoSide.HUMAN) {
                    val coupon = rewardsRepository.claimReward(GameId.BINGO)
                    couponsRepository.addCoupon(coupon)
                    _uiState.update { it.copy(wonCouponId = coupon.id) }
                }
                return@launch
            }

            _uiState.update { it.copy(turn = other, isResolving = false) }
            if (other == BingoSide.COMPUTER) playComputerTurn()
        }
    }

    private fun playComputerTurn() {
        computerJob?.cancel()
        computerJob = viewModelScope.launch {
            delay(COMPUTER_THINK_MILLIS)
            val state = _uiState.value
            BingoBot.chooseCall(state.computerCard, state.myCard, state.called)?.let { call(it, BingoSide.COMPUTER) }
        }
    }

    override fun onCleared() {
        computerJob?.cancel()
    }
}
