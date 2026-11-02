package com.example.playwin.core.domain.repository

import com.example.playwin.core.domain.model.GameId

/** Stands in for a coupon id when a win pays out no coupon. */
const val NO_COUPON_ID = "none"

/**
 * Issues rewards for a confirmed win.
 *
 * This is intentionally separate from [GameEngineRepository]: the engine decides
 * *whether* a round was won, this repository decides *what the player gets* for
 * winning. A signed-in player's coupon is only ever created by the server - the
 * client can't write one - so there is no way to hand this a coupon, only to ask
 * for one. Both calls return the id of the coupon in the wallet, or [NO_COUPON_ID].
 */
interface RewardsRepository {
    /**
     * Claims the coupon for room [roomId]. The server reads the winner from the
     * room, and issues one coupon per room, so this is safe to retry. It's
     * [NO_COUPON_ID] when the server doesn't verify that game's wins yet.
     */
    suspend fun claimRoomReward(roomId: String): Result<String>

    /**
     * Collects any win the signed-in player hasn't been paid for, such as one
     * whose app closed before [claimRoomReward] got through. Safe to call any
     * time: it can't pay a room twice, and failing just leaves it for next time.
     */
    suspend fun claimPendingRewards()

    /**
     * Claims the reward for a win against the computer. No server sees those
     * games, so a signed-in player gets [NO_COUPON_ID]; a guest's coupon lives
     * only in their in-memory demo wallet.
     */
    suspend fun claimVsComputerReward(gameId: GameId): String
}
