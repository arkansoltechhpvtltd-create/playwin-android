package com.example.playwin.feature.game.wordhunt.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.core.domain.wordhunt.WORD_HUNT_TURN_SECONDS
import com.example.playwin.core.domain.wordhunt.WordHuntBoard
import com.example.playwin.core.domain.wordhunt.WordHuntClaim
import com.example.playwin.core.domain.wordhunt.WordHuntScoring
import com.example.playwin.core.domain.wordhunt.matches
import com.example.playwin.feature.game.online.OnlineMatchController
import com.example.playwin.feature.game.online.OnlineMatchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject

@Serializable
data class WordHuntRoomClaim(val word: String, val bonus: Int)

/**
 * What online Word Hunt stores in `rooms.board_state`: each player's claimed
 * words. The grid itself isn't stored - it's derived from the room id (see
 * [WordHuntBoard.forRoom]), so both devices see the same puzzle and neither
 * can choose it. A fresh room's `{}` decodes to "nothing claimed yet".
 */
@Serializable
data class WordHuntRoomState(
    val host: List<WordHuntRoomClaim> = emptyList(),
    val guest: List<WordHuntRoomClaim> = emptyList()
) {
    fun claimsOf(player: Int): List<WordHuntClaim> =
        (if (player == 1) host else guest).map { WordHuntClaim(it.word, it.bonus) }

    fun withClaim(player: Int, claim: WordHuntClaim): WordHuntRoomState {
        val entry = WordHuntRoomClaim(claim.word, claim.bonus)
        return if (player == 1) copy(host = host + entry) else copy(guest = guest + entry)
    }

    val claimedWords: Set<String> get() = (host + guest).map { it.word }.toSet()
}

private val json = Json { ignoreUnknownKeys = true }

fun Room.wordHuntState(): WordHuntRoomState =
    runCatching { json.decodeFromJsonElement<WordHuntRoomState>(state) }.getOrNull() ?: WordHuntRoomState()

fun WordHuntRoomState.toJson(): JsonElement = json.encodeToJsonElement(this)

/**
 * Online head-to-head Word Hunt. Players alternate turns (host first); a
 * find is one room write, and a turn that runs out of time is passed by the
 * player whose turn it was (`0004_turn_guard.sql` makes sure only they can).
 * Each device runs the turn clock locally from when it saw the turn start.
 * Whether the game is decided is confirmed by [GameEngineRepository.resolve].
 */
@HiltViewModel
class WordHuntOnlineViewModel @Inject constructor(
    controllerFactory: OnlineMatchController.Factory,
    private val gameEngineRepository: GameEngineRepository
) : ViewModel() {

    val controller = controllerFactory.create(GameId.WORD_HUNT, viewModelScope)
    val uiState: StateFlow<OnlineMatchState> = controller.state

    private val _secondsLeft = MutableStateFlow(WORD_HUNT_TURN_SECONDS)
    val secondsLeft: StateFlow<Int> = _secondsLeft.asStateFlow()

    private val _selection = MutableStateFlow<Pair<Int, Int>?>(null)
    val selection: StateFlow<Pair<Int, Int>?> = _selection.asStateFlow()

    private var clockJob: Job? = null

    init {
        // Restart the turn clock whenever the turn (or the claims) change.
        viewModelScope.launch {
            uiState
                .map { s -> s.room?.takeIf { it.status == RoomStatus.ACTIVE }?.let { it.currentTurn to it.state } }
                .distinctUntilChanged()
                .collect { turnKey -> if (turnKey == null) clockJob?.cancel() else startClock() }
        }
    }

    fun onCellTap(row: Int, col: Int) {
        val state = uiState.value
        val room = state.room ?: return
        val me = state.myPlayerNumber ?: return
        if (!state.isMyTurn || state.isResolving) return

        val first = _selection.value
        val second = row to col
        if (first == null || first == second) {
            _selection.value = if (first == null) second else null
            return
        }
        _selection.value = null

        val stored = room.wordHuntState()
        val puzzle = WordHuntBoard.forRoom(room.id)
        val match = puzzle.words.firstOrNull { it.word !in stored.claimedWords && it.matches(first, second) } ?: return
        val claim = WordHuntClaim(match.word, WordHuntScoring.bonusFor(_secondsLeft.value))

        controller.runMove {
            clockJob?.cancel()
            val updated = stored.withClaim(me, claim)
            val outcome = gameEngineRepository.resolve(
                GameId.WORD_HUNT,
                GameSnapshot.WordHunt(
                    puzzleWords = puzzle.words.map { it.word },
                    myClaims = updated.claimsOf(me),
                    opponentClaims = updated.claimsOf(3 - me)
                )
            )
            val encoded = updated.toJson()
            when (outcome) {
                GameOutcome.WIN -> controller.submitMove(encoded, null, RoomStatus.FINISHED, winnerPlayer = me)
                GameOutcome.LOSE -> controller.submitMove(encoded, null, RoomStatus.FINISHED, winnerPlayer = 3 - me)
                GameOutcome.DRAW -> controller.submitMove(encoded, null, RoomStatus.FINISHED)
                GameOutcome.IN_PROGRESS -> controller.submitMove(encoded, nextTurnPlayer = 3 - me)
            }
        }
    }

    private fun startClock() {
        clockJob?.cancel()
        _selection.value = null
        clockJob = viewModelScope.launch {
            for (seconds in WORD_HUNT_TURN_SECONDS downTo 0) {
                _secondsLeft.value = seconds
                delay(1000)
            }
            // Out of time on my turn: pass it on without a word.
            val state = uiState.value
            val room = state.room ?: return@launch
            val me = state.myPlayerNumber ?: return@launch
            if (state.isMyTurn) {
                controller.runMove { controller.submitMove(room.state, nextTurnPlayer = 3 - me) }
            }
        }
    }

    override fun onCleared() = controller.onCleared()
}
