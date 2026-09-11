package com.example.playwin.feature.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.CouponCard
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PwSpacing

@Composable
fun CouponsRoute(
    viewModel: CouponsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CouponsScreen(uiState = uiState, onRedeem = viewModel::onRedeem)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponsScreen(
    uiState: CouponsUiState,
    onRedeem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Coupons") }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = PwSpacing.md,
                end = PwSpacing.md,
                top = innerPadding.calculateTopPadding() + PwSpacing.sm,
                bottom = PwSpacing.lg
            ),
            verticalArrangement = Arrangement.spacedBy(PwSpacing.sm)
        ) {
            items(uiState.coupons, key = { it.id }) { coupon ->
                CouponCard(coupon = coupon, onRedeem = { onRedeem(coupon.id) })
            }
        }
    }
}
