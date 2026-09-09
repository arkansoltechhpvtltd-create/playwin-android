package com.example.playwin.core.domain.model

enum class CouponStatus {
    /** Won by the player and ready to redeem. */
    WON,
    /** Not yet unlocked; shown dimmed with its unlock condition. */
    LOCKED,
    /** Already redeemed by the player. */
    REDEEMED
}
