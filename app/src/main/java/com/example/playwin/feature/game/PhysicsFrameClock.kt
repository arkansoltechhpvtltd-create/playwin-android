package com.example.playwin.feature.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive

/**
 * Drives a physics simulation (Carrom, 8-Ball) from the screen's own frame
 * clock, independent of any turn timer: [onTick] gets the seconds elapsed
 * since the last frame.
 */
@Composable
fun PhysicsFrameClock(onTick: (dtSeconds: Float) -> Unit) {
    val tick by rememberUpdatedState(onTick)
    LaunchedEffect(Unit) {
        var lastFrameTimeNanos = 0L
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTimeNanos != 0L) {
                    tick((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f)
                }
                lastFrameTimeNanos = frameTimeNanos
            }
        }
    }
}
