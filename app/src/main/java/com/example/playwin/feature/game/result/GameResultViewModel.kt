package com.example.playwin.feature.game.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.playwin.core.domain.model.Coupon
import com.example.playwin.core.domain.repository.CouponsRepository
import com.example.playwin.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GameResultUiState(
    val coupon: Coupon? = null
)

@HiltViewModel
class GameResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    couponsRepository: CouponsRepository
) : ViewModel() {

    private val couponId: String = checkNotNull(savedStateHandle[Routes.COUPON_ARG])

    val uiState: StateFlow<GameResultUiState> = couponsRepository.observeCoupons()
        .map { coupons -> GameResultUiState(coupon = coupons.find { it.id == couponId }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GameResultUiState()
        )
}
