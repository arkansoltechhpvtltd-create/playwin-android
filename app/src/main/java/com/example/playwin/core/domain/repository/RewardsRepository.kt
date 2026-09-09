package com.example.playwin.core.domain.repository

import com.example.playwin.core.domain.model.Coupon
import com.example.playwin.core.domain.model.GameId

/**
 * Issues rewards for a confirmed win.
 *
 * This is intentionally separate from [GameEngineRepository]: the engine decides
 * *whether* a round was won, this repository decides *what the player gets* for
 * winning. In production both calls would hit a server-authoritative backend so
 * that the client can never fabricate a coupon for itself.
 */
interface RewardsRepository {
    suspend fun claimReward(gameId: GameId): Coupon
}
