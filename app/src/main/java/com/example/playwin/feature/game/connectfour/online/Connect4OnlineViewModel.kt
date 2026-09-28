package com.example.playwin.feature.game.connectfour.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.connectfour.Connect4Logic
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.feature.game.online.OnlineMatchController
import com.example.playwin.feature.game.online.OnlineMatchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject

/** Connect 4's board as stored in the room row: 6 rows of 7 cells, 0 = empty, 1 = host, 2 = guest. */
fun Room.connect4Board(): List<List<Int>> =
    runCatching { Json.decodeFromJsonElement<List<List<Int>>>(state) }
        .getOrNull()
        ?.takeIf { it.size == Connect4Logic.ROWS }
        ?: Connect4Logic.emptyBoard()

/**
 * Online Connect 4. Matchmaking, presence and rewards are the shared
 * [OnlineMatchController]'s job; this ViewModel only turns a column tap into
 * a new board and asks [GameEngineRepository.resolve] whether it ended the
 * game. The host is player 1 and moves first.
 */
@HiltViewModel
class Connect4OnlineViewModel @Inject constructor(
    controllerFactory: OnlineMatchController.Factory,
    private val gameEngineRepository: GameEngineRepository
) : ViewModel() {

    val controller = controllerFactory.create(GameId.CONNECT_FOUR, viewModelScope)
    val uiState: StateFlow<OnlineMatchState> = controller.state

    fun onColumnClick(col: Int) {
        val state = uiState.value
        val room = state.room ?: return
        val me = state.myPlayerNumber ?: return
        val newBoard = Connect4Logic.placePiece(room.connect4Board(), col, me) ?: return

        controller.runMove {
            val outcome = gameEngineRepository.resolve(
                GameId.CONNECT_FOUR,
                GameSnapshot.ConnectFour(board = newBoard, lastPlayer = me, isBoardFull = Connect4Logic.isBoardFull(newBoard))
            )
            val encoded = Json.encodeToJsonElement(newBoard)
            when (outcome) {
                GameOutcome.WIN -> controller.submitMove(encoded, null, RoomStatus.FINISHED, winnerPlayer = me)
                GameOutcome.DRAW -> controller.submitMove(encoded, null, RoomStatus.FINISHED)
                GameOutcome.LOSE, GameOutcome.IN_PROGRESS -> controller.submitMove(encoded, nextTurnPlayer = 3 - me)
            }
        }
    }

    override fun onCleared() = controller.onCleared()
}
