package com.example.playwin.feature.game.bingo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.repository.RewardsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single-player bingo against the caller. Drawing numbers and daubing the
 * card is local mechanics, but whether a daub actually completed a winning
 * row/column/diagonal is always confirmed by [GameEngineRepository.resolve],
 * never decided directly by this ViewModel - same rule as every other game.
 */
@HiltViewModel
class BingoViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BingoUiState())
    val uiState: StateFlow<BingoUiState> = _uiState.asStateFlow()

    fun onCallNumber() {
        val state = _uiState.value
        if (state.hasWon || state.isResolving || state.isBoardExhausted) return
        if (state.calledNumbers.size >= BINGO_MAX_NUMBER) {
            _uiState.update { it.copy(isBoardExhausted = true) }
            return
        }

        var next: Int
        do {
            next = (1..BINGO_MAX_NUMBER).random()
        } while (next in state.calledNumbers)

        _uiState.update { it.copy(calledNumbers = it.calledNumbers + next, currentCall = next) }
    }

    fun onDaub(row: Int, col: Int) {
        val state = _uiState.value
        if (state.hasWon || state.isResolving) return
        if (state.daubed[row][col]) return

        val isFree = row == BINGO_FREE_ROW && col == BINGO_FREE_COL
        if (!isFree && state.card[row][col] !in state.calledNumbers) return

        val newDaubed = state.daubed.mapIndexed { r, rowValues ->
            rowValues.mapIndexed { c, daubed -> daubed || (r == row && c == col) }
        }
        _uiState.update { it.copy(daubed = newDaubed, isResolving = true) }

        viewModelScope.launch {
            val outcome = gameEngineRepository.resolve(GameId.BINGO, GameSnapshot.Bingo(daubed = newDaubed))

            when (outcome) {
                GameOutcome.WIN -> {
                    _uiState.update { it.copy(hasWon = true, isResolving = false) }
                    val coupon = rewardsRepository.claimReward(GameId.BINGO)
                    couponsRepository.addCoupon(coupon)
                    _uiState.update { it.copy(wonCouponId = coupon.id) }
                }

                GameOutcome.LOSE, GameOutcome.DRAW, GameOutcome.IN_PROGRESS -> {
                    _uiState.update { it.copy(isResolving = false) }
                }
            }
        }
    }

    fun onPlayAgain() {
        _uiState.value = BingoUiState()
    }
}
