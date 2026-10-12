package com.example.playwin.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.domain.model.Coupon
import com.example.playwin.core.domain.model.CouponStatus

/**
 * The wallet's hero element: presented like a premium voucher rather than a
 * plain list row — a gold hairline frame, a faint gold sheen, a small
 * "premium" badge and a ticket-style perforation above the redeem code.
 */
@Composable
fun CouponCard(
    coupon: Coupon,
    onRedeem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLocked = coupon.status == CouponStatus.LOCKED
    val isRedeemed = coupon.status == CouponStatus.REDEEMED
    val gold = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isLocked) 0.6f else 1f),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            1.dp,
            if (isLocked) MaterialTheme.colorScheme.outline else gold.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLocked) 0.dp else 2.dp)
    ) {
        val backgroundModifier = if (isLocked) {
            Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        } else {
            Modifier.background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.surfaceVariant, gold.copy(alpha = 0.08f))
                )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(backgroundModifier)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isLocked) {
                        Icon(
                            imageVector = Icons.Filled.WorkspacePremium,
                            contentDescription = null,
                            tint = gold,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    Text(
                        text = coupon.partnerName,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
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
                style = MaterialTheme.typography.headlineSmall,
                color = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant else gold,
                modifier = Modifier.padding(top = 6.dp)
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

                TicketPerforation(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = coupon.code,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
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

/** A thin dashed rule mimicking a ticket's perforation line. */
@Composable
private fun TicketPerforation(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
        )
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
                source = "Won in Sudoku",
                expiry = "Expires 30 Sep 2026",
                code = "BREW30WIN",
                status = CouponStatus.WON
            ),
            onRedeem = {}
        )
    }
}
