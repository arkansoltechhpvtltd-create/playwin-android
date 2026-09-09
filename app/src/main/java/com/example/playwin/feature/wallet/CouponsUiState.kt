package com.example.playwin.feature.wallet

import com.example.playwin.core.domain.model.Coupon

data class CouponsUiState(
    val coupons: List<Coupon> = emptyList(),
    val isLoading: Boolean = true
)
