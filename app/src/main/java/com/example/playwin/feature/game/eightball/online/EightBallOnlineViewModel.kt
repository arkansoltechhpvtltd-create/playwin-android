package com.example.playwin.feature.game.eightball.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.eightball.Ball
import com.example.playwin.core.domain.eightball.BallGroup
import com.example.playwin.core.domain.eightball.EightBallMatch
import com.example.playwin.core.domain.eightball.EightBallPhysics
import com.example.playwin.core.domain.eightball.EightBallRules
import com.example.playwin.core.domain.eightball.Vec2
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.model.Room
import com.example.playwin.core.domain.model.RoomStatus
import com.example.playwin.core.domain.repository.GameEngineRepository
import com.example.playwin.feature.game.eightball.EIGHT_BALL_TURN_SECONDS
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
data class EightBallRoomBall(val id: Int, val x: Float, val y: Float, val p: Boolean = false)

/** The shot that produced the stored table, so the other player can watch it play out. */
@Serializable
data class EightBallRoomShot(val seq: Int, val shooter: Int, val dx: Float, val dy: Float, val power: Float)

/**
 * What online 8-ball stores in `rooms.board_state`: where every ball came to
 * rest after the last shot (the authoritative result), plus that shot for
 * replaying. A fresh room's `{}` decodes to the opening rack.
 */
@Serializable
data class EightBallRoomState(
    val balls: List<EightBallRoomBall>? = null,
    val currentPlayer: Int = 1,
    /** Player number ("1"/"2") to their group, once assigned. */
    val groups: Map<String, String> = emptyMap(),
    val shot: EightBallRoomShot? = null,
    val scratched: Boolean = false
) {
    fun toMatch(): EightBallMatch = EightBallMatch(
        balls = balls?.map { Ball(it.id, Vec2(it.x, it.y), pocketed = it.p) } ?: EightBallMatch().balls,
        currentPlayer = currentPlayer,
        playerGroups = groups.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to BallGroup.valueOf(v) } }.toMap()
    )

    companion object {
        fun of(match: EightBallMatch, shot: EightBallRoomShot?, scratched: Boolean) = EightBallRoomState(
            balls = match.balls.map { EightBallRoomBall(it.id, it.position.x, it.position.y, it.pocketed) },
            currentPlayer = match.currentPlayer,
            groups = match.playerGroups.map { (k, v) -> k.toString() to v.name }.toMap(),
            shot = shot,
            scratched = scratched
        )
    }
}

private val json = Json { ignoreUnknownKeys = true }

fun Room.eightBallState(): EightBallRoomState =
    runCatching { json.decodeFromJsonElement<EightBallRoomState>(state) }.getOrNull() ?: EightBallRoomState()

fun EightBallRoomState.toJson(): JsonElement = json.encodeToJsonElement(this)

/**
 * Online 8-ball via random matchmaking - the same turn-based shot sync as
 * online carrom: the shooter's device runs the physics and writes where the
 * balls came to rest; the other device replays the stored aim for the
 * animation and then snaps to the stored result. `current_turn` follows
 * [EightBallRules] (potting your own group keeps the turn). Whether sinking
 * the 8 won or lost is confirmed by [GameEngineRepository.resolve].
 */
@HiltViewModel
class EightBallOnlineViewModel @Inject constructor(
    controllerFactory: OnlineMatchController.Factory,
    private val gameEngineRepository: GameEngineRepository
) : ViewModel() {

    val controller = controllerFactory.create(GameId.EIGHT_BALL, viewModelScope)
    val uiState: StateFlow<OnlineMatchState> = controller.state

    private enum class Animation { NONE, MY_SHOT, REPLAY }

    /** The balls as drawn - the stored table, or balls in motion during a shot or replay. */
    private val _displayBalls = MutableStateFlow(EightBallMatch().balls)
    val displayBalls: StateFlow<List<Ball>> = _displayBalls.asStateFlow()

    private val _isAnimating = MutableStateFlow(false)
    val isAnimating: StateFlow<Boolean> = _isAnimating.asStateFlow()

    private val _secondsLeft = MutableStateFlow(EIGHT_BALL_TURN_SECONDS)
    val secondsLeft: StateFlow<Int> = _secondsLeft.asStateFlow()

    private var animation = Animation.NONE
    private var replayTarget: List<Ball>? = null
    private var pendingShot: EightBallRoomShot? = null
    private val pocketedThisShot = mutableListOf<Int>()
    private var lastSeenState: EightBallRoomState? = null
    private var clockJob: Job? = null

    init {
        viewModelScope.launch {
            uiState
                .mapNotNull { s -> s.room?.let { it.currentTurn to it.eightBallState() } }
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
        val stored = room.eightBallState()
        pendingShot = EightBallRoomShot((stored.shot?.seq ?: 0) + 1, me, direction.x, direction.y, power)
        _displayBalls.value = EightBallRules.launch(stored.toMatch().balls, direction, power)
    }

    /** Advances whatever is moving by one rendered frame; called from the screen's frame clock. */
    fun onPhysicsTick(dtSeconds: Float) {
        if (animation == Animation.NONE) return
        val result = EightBallPhysics.step(_displayBalls.value, dtSeconds)
        _displayBalls.value = result.balls
        if (animation == Animation.MY_SHOT) pocketedThisShot += result.newlyPocketed
        if (result.anyMoving) return

        when (animation) {
            Animation.MY_SHOT -> submitMyShot(result.balls)
            Animation.REPLAY -> replayTarget?.let { _displayBalls.value = it }
            Animation.NONE -> Unit
        }
        animation = Animation.NONE
        replayTarget = null
        _isAnimating.value = false
    }

    private fun submitMyShot(finalBalls: List<Ball>) {
        val room = uiState.value.room ?: return
        val me = uiState.value.myPlayerNumber ?: return
        val shot = pendingShot ?: return
        val outcome = EightBallRules.resolveShot(room.eightBallState().toMatch(), finalBalls, pocketedThisShot.toList())

        controller.runMove {
            val encoded = EightBallRoomState.of(outcome.after, shot, outcome.scratched).toJson()
            val result = gameEngineRepository.resolve(
                GameId.EIGHT_BALL,
                GameSnapshot.EightBall(
                    shooterClearedGroup = outcome.shooterClearedGroup,
                    eightBallPocketed = outcome.eightBallPocketed,
                    scratched = outcome.scratched
                )
            )
            when (result) {
                GameOutcome.WIN -> controller.submitMove(encoded, null, RoomStatus.FINISHED, winnerPlayer = me)
                GameOutcome.LOSE -> controller.submitMove(encoded, null, RoomStatus.FINISHED, winnerPlayer = 3 - me)
                GameOutcome.DRAW -> controller.submitMove(encoded, null, RoomStatus.FINISHED)
                GameOutcome.IN_PROGRESS -> controller.submitMove(encoded, nextTurnPlayer = outcome.after.currentPlayer)
            }
        }
    }

    private fun onStoredState(stored: EightBallRoomState) {
        val previous = lastSeenState
        lastSeenState = stored
        val me = uiState.value.myPlayerNumber
        val shot = stored.shot
        val isNewOpponentShot = shot != null && shot.shooter != me && previous != null &&
            shot.seq == (previous.shot?.seq ?: 0) + 1

        when {
            // Mid-shot: keep animating; the result is written (and comes back here) once the balls stop.
            animation == Animation.MY_SHOT -> Unit
            isNewOpponentShot && shot != null && previous != null -> {
                animation = Animation.REPLAY
                _isAnimating.value = true
                replayTarget = stored.toMatch().balls
                _displayBalls.value = EightBallRules.launch(previous.toMatch().balls, Vec2(shot.dx, shot.dy), shot.power)
            }
            else -> {
                animation = Animation.NONE
                _isAnimating.value = false
                _displayBalls.value = stored.toMatch().balls
            }
        }
        startClock()
    }

    private fun startClock() {
        clockJob?.cancel()
        if (uiState.value.room?.status != RoomStatus.ACTIVE) return
        clockJob = viewModelScope.launch {
            for (seconds in EIGHT_BALL_TURN_SECONDS downTo 0) {
                _secondsLeft.value = seconds
                delay(1000)
            }
            // Out of time on my turn: pass it on without a shot.
            val state = uiState.value
            val room = state.room ?: return@launch
            if (state.isMyTurn && animation == Animation.NONE) {
                val stored = room.eightBallState()
                val passed = EightBallRules.passTurn(stored.toMatch())
                controller.runMove {
                    controller.submitMove(
                        EightBallRoomState.of(passed, stored.shot, scratched = false).toJson(),
                        nextTurnPlayer = passed.currentPlayer
                    )
                }
            }
        }
    }

    override fun onCleared() = controller.onCleared()
}
