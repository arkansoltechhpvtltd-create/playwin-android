package com.example.playwin.feature.game.rummy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.repository.RewardsRepository
import com.example.playwin.core.domain.rummy.Card
import com.example.playwin.core.domain.rummy.RummyBot
import com.example.playwin.core.domain.rummy.RummyEngine
import com.example.playwin.core.domain.rummy.RummyState
import com.example.playwin.core.domain.rummy.RummyTurn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 13-card Indian Rummy against the computer ([RummyBot]). Dealing, drawing,
 * and discarding are local mechanics delegated to [RummyEngine], but whether
 * a declared hand actually wins is always confirmed by
 * [GameEngineRepository.resolve] via [com.example.playwin.core.domain.rummy.RummyValidator] -
 * never decided directly by this ViewModel.
 */
@HiltViewModel
class RummyViewModel @Inject constructor(
    private val gameEngineRepository: GameEngineRepository,
    private val rewardsRepository: RewardsRepository,
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RummyUiState())
    val uiState: StateFlow<RummyUiState> = _uiState.asStateFlow()

    fun onDrawFromShoe() {
        val state = _uiState.value
        if (!state.canDraw) return
        _uiState.update { it.copy(game = RummyEngine.drawFromShoe(state.game)) }
    }

    fun onDrawFromDiscard() {
        val state = _uiState.value
        if (!state.canDraw || state.game.discardPile.isEmpty()) return
        _uiState.update { it.copy(game = RummyEngine.drawFromDiscard(state.game)) }
    }

    fun onCardTap(card: Card) {
        val state = _uiState.value
        if (!state.canDiscard) return
        _uiState.update { it.copy(selectedCard = if (it.selectedCard?.id == card.id) null else card) }
    }

    /** Moves a card within the player's on-screen arrangement; what they hold is unchanged. */
    fun onMoveCard(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val ids = state.arrangedHand.map { it.id }
            state.copy(handOrder = HandArrangement.move(ids, fromIndex, toIndex))
        }
    }

    fun onConfirmDiscard() {
        val state = _uiState.value
        val card = state.selectedCard ?: return
        if (!state.canConfirmDiscard) return

        val afterDiscard = RummyEngine.discard(state.game, card)
        _uiState.update { it.copy(game = afterDiscard, selectedCard = null) }
        runBotTurnIfNeeded(afterDiscard)
    }

    fun onDeclare() {
        val state = _uiState.value
        val setAside = state.selectedCard ?: return
        if (!state.canDeclare) return

        val finalHand = state.game.playerHand.filterNot { it.id == setAside.id }
        _uiState.update { it.copy(isResolving = true, selectedCard = null) }

        viewModelScope.launch {
            val outcome = gameEngineRepository.resolve(
                GameId.RUMMY,
                GameSnapshot.Rummy(declaredHand = finalHand, wildRank = state.game.wildRank)
            )

            when (outcome) {
                GameOutcome.WIN -> {
                    _uiState.update { it.copy(hasWon = true, isResolving = false) }
                    val coupon = rewardsRepository.claimReward(GameId.RUMMY)
                    couponsRepository.addCoupon(coupon)
                    _uiState.update { it.copy(wonCouponId = coupon.id) }
                }

                GameOutcome.LOSE, GameOutcome.DRAW, GameOutcome.IN_PROGRESS -> {
                    _uiState.update { it.copy(hasLost = true, isResolving = false) }
                }
            }
        }
    }

    fun onPlayAgain() {
        _uiState.value = RummyUiState()
    }

    private fun runBotTurnIfNeeded(game: RummyState) {
        if (game.turn != RummyTurn.BOT) return
        _uiState.update { it.copy(isBotThinking = true) }
        viewModelScope.launch {
            delay(700) // brief pause so the player can see the bot's turn happen
            val turn = withContext(Dispatchers.Default) { RummyBot.playTurn(game) }
            when (turn) {
                is RummyBot.Turn.Played -> _uiState.update { it.copy(game = turn.state, isBotThinking = false) }
                is RummyBot.Turn.Declared -> resolveComputerDeclaration(turn)
            }
        }
    }

    /** The computer's declaration is confirmed by the game engine like the player's; an invalid one hands the player the round. */
    private suspend fun resolveComputerDeclaration(turn: RummyBot.Turn.Declared) {
        _uiState.update { it.copy(game = turn.state, isResolving = true) }
        val outcome = gameEngineRepository.resolve(
            GameId.RUMMY,
            GameSnapshot.Rummy(declaredHand = turn.declaredHand, wildRank = turn.state.wildRank)
        )
        if (outcome == GameOutcome.WIN) {
            _uiState.update {
                it.copy(computerDeclared = true, computerDeclaredHand = turn.declaredHand, isResolving = false, isBotThinking = false)
            }
        } else {
            _uiState.update {
                it.copy(hasWon = true, computerDeclaredHand = turn.declaredHand, isResolving = false, isBotThinking = false)
            }
            val coupon = rewardsRepository.claimReward(GameId.RUMMY)
            couponsRepository.addCoupon(coupon)
            _uiState.update { it.copy(wonCouponId = coupon.id) }
        }
    }
}
