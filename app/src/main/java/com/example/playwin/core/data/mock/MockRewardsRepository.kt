package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.Coupon
import com.example.playwin.core.domain.model.CouponStatus
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.repository.NO_COUPON_ID
import com.example.playwin.core.domain.repository.RewardsRepository
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Demo rewards for guests, whose wallet lives only in memory and never reaches
 * Supabase. Signed-in players go through SupabaseRewardsRepository instead.
 */
@Singleton
class MockRewardsRepository @Inject constructor(
    private val mockCouponsRepository: MockCouponsRepository
) : RewardsRepository {

    private val partners = listOf(
        Triple("Brew & Bean Cafe", "30% off", "BREW"),
        Triple("StreetStyle Fashion", "₹200 off", "STYLE"),
        Triple("PizzaPlace", "Buy 1 get 1", "PIZZA"),
        Triple("CineMax", "20% off tickets", "CINE"),
        Triple("FitZone Gym", "1 free session", "FIT"),
        Triple("QuickMart", "15% off groceries", "MART")
    )

    // Rooms need a real account, so a guest never has one to claim.
    override suspend fun claimRoomReward(roomId: String): Result<String> = Result.success(NO_COUPON_ID)

    override suspend fun claimVsComputerReward(gameId: GameId): String {
        // Simulated network latency, as if this were a real backend call.
        delay(400)

        val (partner, discount, prefix) = partners.random()
        val code = "$prefix${Random.nextInt(1000, 9999)}"

        val coupon = Coupon(
            id = "reward-${System.currentTimeMillis()}-${Random.nextInt(1000)}",
            partnerName = partner,
            discountText = discount,
            source = "Won in ${gameId.displayName()}",
            expiry = "Expires in 30 days",
            code = code,
            status = CouponStatus.WON
        )
        mockCouponsRepository.addCoupon(coupon)
        return coupon.id
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
