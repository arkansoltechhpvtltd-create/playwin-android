package com.example.playwin.feature.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    LaunchedEffect(Unit) { viewModel.onOpened() }
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
            if (!uiState.isLoading && uiState.coupons.isEmpty()) {
                item { EmptyCoupons(modifier = Modifier.fillParentMaxHeight(0.7f)) }
            }
            items(uiState.coupons, key = { it.id }) { coupon ->
                CouponCard(coupon = coupon, onRedeem = { onRedeem(coupon.id) })
            }
        }
    }
}

@Composable
private fun EmptyCoupons(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = PwSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ConfirmationNumber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = "No coupons yet",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = PwSpacing.md)
        )
        Text(
            text = "Win any game to earn one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = PwSpacing.xs)
        )
    }
}
