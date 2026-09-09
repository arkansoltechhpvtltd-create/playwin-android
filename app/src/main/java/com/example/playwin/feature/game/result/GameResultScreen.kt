package com.example.playwin.feature.game.result

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.CouponCard
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.domain.model.Coupon
import com.example.playwin.core.domain.model.CouponStatus

@Composable
fun GameResultRoute(
    onBackToGames: () -> Unit,
    viewModel: GameResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GameResultScreen(coupon = uiState.coupon, onBackToGames = onBackToGames)
}

@Composable
fun GameResultScreen(
    coupon: Coupon?,
    onBackToGames: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .size(72.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = "You won!",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Here's your coupon - it's already in your wallet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            if (coupon != null) {
                CouponCard(
                    coupon = coupon,
                    onRedeem = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = onBackToGames,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Text("Back to games")
            }
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
                source = "Won in Connect 4",
                expiry = "Expires in 30 days",
                code = "BREW4821",
                status = CouponStatus.WON
            ),
            onBackToGames = {}
        )
    }
}
