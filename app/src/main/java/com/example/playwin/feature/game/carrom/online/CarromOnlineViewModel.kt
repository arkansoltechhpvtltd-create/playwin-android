package com.example.playwin.feature.game.carrom.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.carrom.CarromMatch
import com.example.playwin.core.domain.carrom.CarromPhysics
import com.example.playwin.core.domain.carrom.CarromRules
import com.example.playwin.core.domain.carrom.Coin
import com.example.playwin.core.domain.carrom.CoinColor
import com.example.playwin.core.domain.carrom.Vec2
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.feature.game.carrom.CARROM_TURN_SECONDS
import com.example.playwin.feature.game.online.OnlineMatchController
import com.example.playwin.feature.game.online.OnlineMatchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject

@Serializable
data class CarromRoomCoin(val id: Int, val x: Float, val y: Float, val p: Boolean = false)

/** The shot that produced the stored position, so the other player can watch it play out. */
@Serializable
data class CarromRoomShot(val seq: Int, val shooter: Int, val dx: Float, val dy: Float, val power: Float)

/**
 * What online carrom stores in `rooms.board_state`: where every piece came to
 * rest after the last shot (the authoritative result), plus that shot for
 * replaying. A fresh room's `{}` decodes to the opening setup.
 */
@Serializable
data class CarromRoomState(
    val coins: List<CarromRoomCoin>? = null,
    val currentPlayer: Int = 1,
    /** Player number ("1"/"2") to their coin color, once assigned. */
    val colors: Map<String, String> = emptyMap(),
    val scores: List<Int> = listOf(0, 0),
    val shot: CarromRoomShot? = null,
    val fouled: Boolean = false
) {
    fun toMatch(): CarromMatch {
        val base = CarromMatch()
        return CarromMatch(
            coins = coins?.map { Coin(it.id, Vec2(it.x, it.y), pocketed = it.p) } ?: base.coins,
            currentPlayer = currentPlayer,
            playerColors = colors.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to CoinColor.valueOf(v) } }.toMap(),
            scores = mapOf(1 to scores.getOrElse(0) { 0 }, 2 to scores.getOrElse(1) { 0 })
        )
    }

    companion object {
        fun of(match: CarromMatch, shot: CarromRoomShot?, fouled: Boolean) = CarromRoomState(
            coins = match.coins.map { CarromRoomCoin(it.id, it.position.x, it.position.y, it.pocketed) },
            currentPlayer = match.currentPlayer,
            colors = match.playerColors.map { (k, v) -> k.toString() to v.name }.toMap(),
            scores = listOf(match.scores.getValue(1), match.scores.getValue(2)),
            shot = shot,
            fouled = fouled
        )
    }
}

private val json = Json { ignoreUnknownKeys = true }

fun Room.carromState(): CarromRoomState =
    runCatching { json.decodeFromJsonElement<CarromRoomState>(state) }.getOrNull() ?: CarromRoomState()

fun CarromRoomState.toJson(): JsonElement = json.encodeToJsonElement(this)

/**
 * Online carrom via random matchmaking. Shots are turn-based: the shooter's
 * device runs the physics for their shot and writes where everything came
 * to rest, and the room's `current_turn` follows [CarromRules] (a scoring
 * shot keeps the turn). The other device replays the shot from the stored
 * aim for the animation, then snaps to the stored result, so tiny
 * floating-point differences between devices can never leave the two
 * boards disagreeing. The shooter's device passes the turn if its shot
 * clock runs out. Who has won is confirmed by [GameEngineRepository.resolve].
 */
@HiltViewModel
class CarromOnlineViewModel @Inject constructor(
    controllerFactory: OnlineMatchController.Factory,
    private val gameEngineRepository: GameEngineRepository
) : ViewModel() {

    val controller = controllerFactory.create(GameId.CARROM, viewModelScope)
    val uiState: StateFlow<OnlineMatchState> = controller.state

    private enum class Animation { NONE, MY_SHOT, REPLAY }

    /** The pieces as drawn - the stored position, or pieces in motion during a shot or replay. */
    private val _displayCoins = MutableStateFlow(CarromMatch().coins)
    val displayCoins: StateFlow<List<Coin>> = _displayCoins.asStateFlow()

    private val _isAnimating = MutableStateFlow(false)
    val isAnimating: StateFlow<Boolean> = _isAnimating.asStateFlow()

    private val _secondsLeft = MutableStateFlow(CARROM_TURN_SECONDS)
    val secondsLeft: StateFlow<Int> = _secondsLeft.asStateFlow()

    private var animation = Animation.NONE
    private var replayTarget: List<Coin>? = null
    private var pendingShot: CarromRoomShot? = null
    private val pocketedThisShot = mutableListOf<Int>()
    private var lastSeenState: CarromRoomState? = null
    private var clockJob: Job? = null

    init {
        viewModelScope.launch {
            uiState
                .mapNotNull { s -> s.room?.let { it.currentTurn to it.carromState() } }
                .distinctUntilChanged()
                .collect { (_, stored) -> onStoredState(stored) }
        }
    }

    fun onShoot(direction: Vec2, power: Float) {
        val state = uiState.value
        val room = state.room ?: return
        val me = state.myPlayerNumber ?: return
        if (!state.isMyTurn || state.isResolving || animation != Animation.NONE) return
        clockJob?.cancel()
        pocketedThisShot.clear()
        animation = Animation.MY_SHOT
        _isAnimating.value = true
        pendingShot = CarromRoomShot(nextSeq(room), me, direction.x, direction.y, power)
        _displayCoins.value = CarromRules.launch(room.carromState().toMatch().coins, direction, power)
    }

    /** Advances whatever is moving by one rendered frame; called from the screen's frame clock. */
    fun onPhysicsTick(dtSeconds: Float) {
        if (animation == Animation.NONE) return
        val result = CarromPhysics.step(_displayCoins.value, dtSeconds)
        _displayCoins.value = result.coins
        if (animation == Animation.MY_SHOT) pocketedThisShot += result.newlyPocketed
        if (result.anyMoving) return

        when (animation) {
            Animation.MY_SHOT -> submitMyShot(result.coins)
            Animation.REPLAY -> replayTarget?.let { _displayCoins.value = it }
            Animation.NONE -> Unit
        }
        animation = Animation.NONE
        replayTarget = null
        _isAnimating.value = false
    }

    private fun submitMyShot(finalCoins: List<Coin>) {
        val room = uiState.value.room ?: return
        val me = uiState.value.myPlayerNumber ?: return
        val shot = pendingShot ?: return
        val outcome = CarromRules.resolveShot(room.carromState().toMatch(), finalCoins, pocketedThisShot.toList())
        val after = outcome.after

        controller.runMove {
            val encoded = CarromRoomState.of(after, shot, outcome.fouled).toJson()
            val result = gameEngineRepository.resolve(
                GameId.CARROM,
                GameSnapshot.Carrom(
                    boardCleared = after.boardCleared,
                    shooterScore = after.scores.getValue(me),
                    opponentScore = after.scores.getValue(3 - me)
                )
            )
            when (result) {
                GameOutcome.WIN -> controller.submitMove(encoded, null, RoomStatus.FINISHED, winnerPlayer = me)
                GameOutcome.LOSE -> controller.submitMove(encoded, null, RoomStatus.FINISHED, winnerPlayer = 3 - me)
                GameOutcome.DRAW -> controller.submitMove(encoded, null, RoomStatus.FINISHED)
                GameOutcome.IN_PROGRESS -> controller.submitMove(encoded, nextTurnPlayer = after.currentPlayer)
            }
        }
    }

    private fun onStoredState(stored: CarromRoomState) {
        val previous = lastSeenState
        lastSeenState = stored
        val me = uiState.value.myPlayerNumber
        val shot = stored.shot
        val isNewOpponentShot = shot != null && shot.shooter != me && previous != null &&
            shot.seq == (previous.shot?.seq ?: 0) + 1

        when {
            // Mid-shot: keep animating; the result is written (and comes back here) once the pieces stop.
            animation == Animation.MY_SHOT -> Unit
            isNewOpponentShot && shot != null && previous != null -> {
                animation = Animation.REPLAY
                _isAnimating.value = true
                replayTarget = stored.toMatch().coins
                _displayCoins.value = CarromRules.launch(previous.toMatch().coins, Vec2(shot.dx, shot.dy), shot.power)
            }
            else -> {
                animation = Animation.NONE
                _isAnimating.value = false
                _displayCoins.value = stored.toMatch().coins
            }
        }
        startClock()
    }

    private fun nextSeq(room: Room): Int = (room.carromState().shot?.seq ?: 0) + 1

    private fun startClock() {
        clockJob?.cancel()
        if (uiState.value.room?.status != RoomStatus.ACTIVE) return
        clockJob = viewModelScope.launch {
            for (seconds in CARROM_TURN_SECONDS downTo 0) {
                _secondsLeft.value = seconds
                delay(1000)
            }
            // Out of time on my turn: pass it on without a shot.
            val state = uiState.value
            val room = state.room ?: return@launch
            if (state.isMyTurn && animation == Animation.NONE) {
                val stored = room.carromState()
                val passed = CarromRules.passTurn(stored.toMatch())
                controller.runMove {
                    controller.submitMove(
                        CarromRoomState.of(passed, stored.shot, fouled = false).toJson(),
                        nextTurnPlayer = passed.currentPlayer
                    )
                }
            }
        }
    }

    override fun onCleared() = controller.onCleared()
}
