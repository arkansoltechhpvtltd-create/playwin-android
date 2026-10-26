package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.Coupon
import com.example.playwin.core.domain.model.CouponStatus
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.repository.RewardsRepository
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Mock stand-in for a server-authoritative rewards endpoint. Every winning
 * round asks this repository - never the UI - for the coupon that was won.
 * Swap this implementation for a network call and nothing above it changes.
 */
@Singleton
class MockRewardsRepository @Inject constructor() : RewardsRepository {

    private val partners = listOf(
        Triple("Brew & Bean Cafe", "30% off", "BREW"),
        Triple("StreetStyle Fashion", "₹200 off", "STYLE"),
        Triple("PizzaPlace", "Buy 1 get 1", "PIZZA"),
        Triple("CineMax", "20% off tickets", "CINE"),
        Triple("FitZone Gym", "1 free session", "FIT"),
        Triple("QuickMart", "15% off groceries", "MART")
    )

    override suspend fun claimReward(gameId: GameId): Coupon {
        // Simulated network latency, as if this were a real backend call.
        delay(400)

        val (partner, discount, prefix) = partners.random()
        val code = "$prefix${Random.nextInt(1000, 9999)}"

        return Coupon(
            id = "reward-${System.currentTimeMillis()}-${Random.nextInt(1000)}",
            partnerName = partner,
            discountText = discount,
            source = "Won in ${gameId.displayName()}",
            expiry = "Expires in 30 days",
            code = code,
            status = CouponStatus.WON
        )
    }

    private fun GameId.displayName(): String = when (this) {
        GameId.TWENTY_FORTY_EIGHT -> "2048"
        GameId.EIGHT_BALL -> "8-Ball"
        GameId.CARROM -> "Carrom"
        GameId.CHINESE_CHECKERS -> "Chinese Checkers"
        GameId.SUDOKU -> "Sudoku"
        GameId.SNAKES_AND_LADDERS -> "Snakes & Ladders"
        GameId.CHESS -> "Chess"
        GameId.LUDO -> "Ludo"
        GameId.RUMMY -> "Rummy"
    }
}
