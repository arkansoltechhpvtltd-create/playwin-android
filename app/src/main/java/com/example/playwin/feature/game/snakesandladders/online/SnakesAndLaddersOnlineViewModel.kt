package com.example.playwin.feature.game.snakesandladders.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.snakesandladders.SnakesAndLaddersRules
import com.example.playwin.feature.game.online.OnlineMatchController
import com.example.playwin.feature.game.online.OnlineMatchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Online 1v1 Snakes and Ladders (host rolls first). Matchmaking, presence and
 * rewards are the shared [OnlineMatchController]'s job. Taking a turn is just
 * asking the server to roll ([OnlineMatchController.rollAndMove]): the
 * backend throws the die, applies the move, decides whose turn is next (the
 * same player after a 6) and whether it won, and writes it all to the room -
 * this client never supplies a roll or a board. Both players' screens then
 * replay that stored roll, so they see the same walk, ladder or snake.
 */
@HiltViewModel
class SnakesAndLaddersOnlineViewModel @Inject constructor(
    controllerFactory: OnlineMatchController.Factory
) : ViewModel() {

    val controller = controllerFactory.create(GameId.SNAKES_AND_LADDERS, viewModelScope)
    val uiState: StateFlow<OnlineMatchState> = controller.state

    fun onRoll() {
        val state = uiState.value
        val room = state.room ?: return
        val me = state.myPlayerNumber ?: return
        val game = room.snakesAndLaddersState()
        if (game.turn != snakesAndLaddersPlayerIndex(me) || SnakesAndLaddersRules.winner(game) != null) return

        controller.runMove { controller.rollAndMove() }
    }

    override fun onCleared() = controller.onCleared()
}
