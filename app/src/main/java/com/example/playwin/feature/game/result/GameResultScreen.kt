package com.example.playwin.feature.game.result

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.CouponCard
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.WinCelebration
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwSpacing
import com.example.playwin.core.domain.model.Coupon
import com.example.playwin.core.domain.model.CouponStatus

@Composable
fun GameResultRoute(
    onBackToGames: () -> Unit,
    viewModel: GameResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GameResultScreen(coupon = uiState.coupon, hasCoupon = uiState.hasCoupon, onBackToGames = onBackToGames)
}

@Composable
fun GameResultScreen(
    coupon: Coupon?,
    hasCoupon: Boolean,
    onBackToGames: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = PwSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WinCelebration(modifier = Modifier.fillMaxWidth())

            Text(
                text = "You won!",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.padding(top = PwSpacing.sm)
            )
            Text(
                text = if (hasCoupon) {
                    "Here's your coupon — it's already in your wallet."
                } else {
                    "This game doesn't award coupons yet."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = PwSpacing.lg)
            )

            if (coupon != null) {
                CouponCard(
                    coupon = coupon,
                    onRedeem = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }

            PrimaryButton(
                text = "Back to games",
                onClick = onBackToGames,
                modifier = Modifier.padding(top = PwSpacing.xl, bottom = PwSpacing.lg)
            )
        }
    }
}

@Preview
@Composable
private fun GameResultScreenPreview() {
    PlayWinTheme {
        GameResultScreen(
            coupon = Coupon(
                id = "1",
                partnerName = "Brew & Bean Cafe",
                discountText = "30% off",
                source = "Won in Chinese Checkers",
                expiry = "Expires in 30 days",
                code = "BREW4821",
                status = CouponStatus.WON
            ),
            hasCoupon = true,
            onBackToGames = {}
        )
    }
}
