package com.example.playwin.core.data.mock

import com.example.playwin.core.domain.model.Coupon
import com.example.playwin.core.domain.model.CouponStatus
import com.example.playwin.core.domain.repository.CouponsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory coupon wallet, pre-populated with a couple of already-won coupons
 * plus some locked ones so the Coupons tab has something to show on first run.
 */
@Singleton
class MockCouponsRepository @Inject constructor() : CouponsRepository {

    private val coupons = MutableStateFlow(
        listOf(
            Coupon(
                id = "seed-1",
                partnerName = "Brew & Bean Cafe",
                discountText = "30% off",
                source = "Won in Sudoku",
                expiry = "Expires 30 Sep 2026",
                code = "BREW30WIN",
                status = CouponStatus.WON
            ),
            Coupon(
                id = "seed-2",
                partnerName = "StreetStyle Fashion",
                discountText = "₹200 off",
                source = "Won in Chinese Checkers",
                expiry = "Expires 15 Oct 2026",
                code = "STYLE200X",
                status = CouponStatus.WON
            ),
            Coupon(
                id = "seed-3",
                partnerName = "PizzaPlace",
                discountText = "Buy 1 get 1",
                source = "Locked",
                expiry = "",
                code = "",
                status = CouponStatus.LOCKED,
                unlockCondition = "Win 3 games this week"
            ),
            Coupon(
                id = "seed-4",
                partnerName = "CineMax",
                discountText = "20% off tickets",
                source = "Locked",
                expiry = "",
                code = "",
                status = CouponStatus.LOCKED,
                unlockCondition = "Reach a 3-day play streak"
            )
        )
    )

    override fun observeCoupons(): Flow<List<Coupon>> = coupons.asStateFlow()

    override fun observeCouponBalance(): Flow<Int> =
        coupons.map { list -> list.count { it.status == CouponStatus.WON } }

    override suspend fun addCoupon(coupon: Coupon) {
        coupons.value = listOf(coupon) + coupons.value
    }

    override suspend fun redeemCoupon(couponId: String) {
        coupons.value = coupons.value.map {
            if (it.id == couponId) it.copy(status = CouponStatus.REDEEMED) else it
        }
    }
}
