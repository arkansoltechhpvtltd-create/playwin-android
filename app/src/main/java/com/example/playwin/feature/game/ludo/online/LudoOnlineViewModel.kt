package com.example.playwin.feature.game.ludo.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.ludo.LudoEngine
import com.example.playwin.core.domain.ludo.Token
import com.example.playwin.core.domain.ludo.currentColor
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.model.RoomStatus
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.feature.game.online.OnlineMatchController
import com.example.playwin.feature.game.online.OnlineMatchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Online 1v1 Ludo (host = Red, guest = Yellow). Matchmaking, presence and
 * rewards are the shared [OnlineMatchController]'s job. A turn is up to two
 * room writes: the roll (so the opponent sees it), then the token move - or
 * a single write that records the roll and passes, if nothing can move. The
 * room's `current_turn` follows [LudoEngine]'s turn order, including the
 * extra roll for a 6, a capture or a token reaching home, and the backend
 * rejects a write from anyone else.
 * Whether a move won is still confirmed by [GameEngineRepository.resolve].
 */
@HiltViewModel
class LudoOnlineViewModel @Inject constructor(
    controllerFactory: OnlineMatchController.Factory,
    private val gameEngineRepository: GameEngineRepository
) : ViewModel() {

    val controller = controllerFactory.create(GameId.LUDO, viewModelScope)
    val uiState: StateFlow<OnlineMatchState> = controller.state

    fun onRollDice() {
        val room = uiState.value.room ?: return
        val game = room.ludoState()
        if (game.diceValue != null) return

        controller.runMove {
            // The die is the game engine's, as against the computer - never thrown here.
            val rolled = LudoEngine.rollDice(game, gameEngineRepository.rollDie(GameId.LUDO))
            val dice = rolled.diceValue
            if (LudoEngine.movableTokens(rolled).isEmpty()) {
                val passed = LudoEngine.passTurn(rolled)
                controller.submitMove(passed.toRoomJson(lastRoll = dice), nextTurnPlayer = ludoPlayerNumber(passed.currentColor))
            } else {
                controller.submitMove(rolled.toRoomJson(lastRoll = dice), nextTurnPlayer = ludoPlayerNumber(rolled.currentColor))
            }
        }
    }

    fun onTokenTap(token: Token) {
        val state = uiState.value
        val room = state.room ?: return
        val me = state.myPlayerNumber ?: return
        val game = room.ludoState()
        if (token.color != ludoColorOf(me)) return
        if (LudoEngine.movableTokens(game).none { it.color == token.color && it.index == token.index }) return

        controller.runMove {
            val lastRoll = game.diceValue
            val after = LudoEngine.applyMove(game, token.color, token.index)
            val moverPositions = after.tokens.filter { it.color == token.color }.map { it.position }
            val encoded = after.toRoomJson(lastRoll)
            when (gameEngineRepository.resolve(GameId.LUDO, GameSnapshot.Ludo(moverPositions))) {
                GameOutcome.WIN -> controller.submitMove(encoded, null, RoomStatus.FINISHED, winnerPlayer = me)
                GameOutcome.LOSE, GameOutcome.DRAW, GameOutcome.IN_PROGRESS ->
                    controller.submitMove(encoded, nextTurnPlayer = ludoPlayerNumber(after.currentColor))
            }
        }
    }

    override fun onCleared() = controller.onCleared()
}
