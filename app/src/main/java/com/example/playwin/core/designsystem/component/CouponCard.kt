package com.example.playwin.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.domain.model.Coupon
import com.example.playwin.core.domain.model.CouponStatus

@Composable
fun CouponCard(
    coupon: Coupon,
    onRedeem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLocked = coupon.status == CouponStatus.LOCKED
    val isRedeemed = coupon.status == CouponStatus.REDEEMED

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isLocked) 0.55f else 1f),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = coupon.partnerName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (isLocked) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = coupon.discountText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = if (isLocked) coupon.unlockCondition.orEmpty() else coupon.source,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (!isLocked) {
                Text(
                    text = coupon.expiry,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = coupon.code,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                    )
                    PrimaryButton(
                        text = if (isRedeemed) "Redeemed" else "Redeem",
                        onClick = onRedeem,
                        enabled = !isRedeemed,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun CouponCardWonPreview() {
    PlayWinTheme {
        CouponCard(
            coupon = Coupon(
                id = "1",
                partnerName = "Brew & Bean Cafe",
                discountText = "30% off",
                source = "Won in Bingo",
                expiry = "Expires 30 Sep 2026",
                code = "BREW30WIN",
                status = CouponStatus.WON
            ),
            onRedeem = {}
        )
    }
}
