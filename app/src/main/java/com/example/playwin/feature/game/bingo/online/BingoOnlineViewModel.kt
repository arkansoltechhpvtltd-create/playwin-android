package com.example.playwin.feature.game.bingo.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.bingo.BingoRules
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.feature.game.bingo.BingoCallResult
import com.example.playwin.feature.game.bingo.resolveBingoCall
import com.example.playwin.feature.game.online.OnlineMatchController
import com.example.playwin.feature.game.online.OnlineMatchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject

/**
 * What online bingo stores in `rooms.board_state`: just the calls. The two
 * cards aren't stored at all - they're derived from the room id (see
 * [BingoRules.cardsForRoom]), so neither player can choose their card. A
 * fresh room's `{}` decodes to "nothing called yet".
 */
@Serializable
data class BingoRoomState(
    val called: List<Int> = emptyList(),
    /** 1 = host, 2 = guest; who made [called]'s last call. */
    val lastCaller: Int? = null
)

private val json = Json { ignoreUnknownKeys = true }

fun Room.bingoState(): BingoRoomState =
    runCatching { json.decodeFromJsonElement<BingoRoomState>(state) }.getOrNull() ?: BingoRoomState()

fun BingoRoomState.toJson(): JsonElement = json.encodeToJsonElement(this)

/** The card belonging to player [number] (1 = host, 2 = guest) in [room]. */
fun bingoCardFor(room: Room, number: Int): List<List<Int>> {
    val (host, guest) = BingoRules.cardsForRoom(room.id)
    return if (number == 1) host else guest
}

/**
 * Online head-to-head bingo. Players alternate calls (host first); each call
 * is one room write that the backend only accepts from the player whose
 * turn it is. Matchmaking, presence and rewards are the shared
 * [OnlineMatchController]'s job; whether a call completed a line is
 * confirmed by [GameEngineRepository.resolve].
 */
@HiltViewModel
class BingoOnlineViewModel @Inject constructor(
    controllerFactory: OnlineMatchController.Factory,
    private val gameEngineRepository: GameEngineRepository
) : ViewModel() {

    val controller = controllerFactory.create(GameId.BINGO, viewModelScope)
    val uiState: StateFlow<OnlineMatchState> = controller.state

    fun onCallNumber(number: Int) {
        val room = uiState.value.room ?: return
        val me = uiState.value.myPlayerNumber ?: return
        if (number in room.bingoState().called || bingoCardFor(room, me).none { number in it }) return
        call(number)
    }

    fun onCallRandom() {
        val room = uiState.value.room ?: return
        BingoRules.randomUncalled(room.bingoState().called)?.let(::call)
    }

    private fun call(number: Int) {
        val room = uiState.value.room ?: return
        val me = uiState.value.myPlayerNumber ?: return
        val opponent = 3 - me

        controller.runMove {
            val called = room.bingoState().called + number
            val encoded = BingoRoomState(called = called, lastCaller = me).toJson()
            when (gameEngineRepository.resolveBingoCall(bingoCardFor(room, me), bingoCardFor(room, opponent), called)) {
                BingoCallResult.CALLER_WINS -> controller.submitMove(encoded, null, RoomStatus.FINISHED, winnerPlayer = me)
                BingoCallResult.OTHER_WINS -> controller.submitMove(encoded, null, RoomStatus.FINISHED, winnerPlayer = opponent)
                BingoCallResult.NO_WINNER -> controller.submitMove(encoded, nextTurnPlayer = opponent)
            }
        }
    }

    override fun onCleared() = controller.onCleared()
}
