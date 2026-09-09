package com.example.playwin.core.domain.model

/**
 * A discount coupon a player can win by playing games.
 * Illustrative / mock rewards only - no real partner offers.
 */
data class Coupon(
    val id: String,
    val partnerName: String,
    val discountText: String,
    val source: String,
    val expiry: String,
    val code: String,
    val status: CouponStatus,
    val unlockCondition: String? = null
)
