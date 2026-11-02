package com.example.playwin.core.domain.repository

import com.example.playwin.core.domain.model.Coupon
import kotlinx.coroutines.flow.Flow

/** The player's coupon wallet. It can't add a coupon: see [RewardsRepository]. */
interface CouponsRepository {
    fun observeCoupons(): Flow<List<Coupon>>

    /** Number of unredeemed, won coupons - shown as the lobby's balance chip. */
    fun observeCouponBalance(): Flow<Int>

    suspend fun redeemCoupon(couponId: String)
}
