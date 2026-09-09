package com.example.playwin.feature.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.repository.CouponsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CouponsViewModel @Inject constructor(
    private val couponsRepository: CouponsRepository
) : ViewModel() {

    val uiState: StateFlow<CouponsUiState> = couponsRepository.observeCoupons()
        .map { coupons -> CouponsUiState(coupons = coupons, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CouponsUiState()
        )

    fun onRedeem(couponId: String) {
        viewModelScope.launch {
            couponsRepository.redeemCoupon(couponId)
        }
    }
}
