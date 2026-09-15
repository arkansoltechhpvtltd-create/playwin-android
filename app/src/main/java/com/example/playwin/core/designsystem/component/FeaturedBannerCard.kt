package com.example.playwin.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.playwin.core.designsystem.theme.LocalPwIsDarkTheme
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwBrandGradientEnd
import com.example.playwin.core.designsystem.theme.PwBrandGradientStart
import com.example.playwin.core.designsystem.theme.PwGoldContainerLight
import com.example.playwin.core.designsystem.theme.PwInkText
import com.example.playwin.core.designsystem.theme.PwIvorySurface
import com.example.playwin.core.designsystem.theme.PwMoonlight

/**
 * The lobby's featured banner - the app's signature "walk into the lounge"
 * moment. Dark mode keeps a deep ink-to-emerald gradient; light mode gets its
 * own soft warm-to-gold sheen so it reads as a card genuinely designed for a
 * light page rather than a dark panel dropped on top of it. Both share the
 * same gold frame and headline so the two themes still feel like one brand.
 */
@Composable
fun FeaturedBannerCard(
    modifier: Modifier = Modifier
) {
    val isDark = LocalPwIsDarkTheme.current
    val gold = MaterialTheme.colorScheme.primary

    val backgroundBrush = if (isDark) {
        Brush.linearGradient(listOf(PwBrandGradientStart, PwBrandGradientEnd))
    } else {
        Brush.linearGradient(listOf(PwIvorySurface, PwGoldContainerLight))
    }
    val containerColor = if (isDark) PwBrandGradientStart else PwIvorySurface
    val borderAlpha = if (isDark) 0.5f else 0.85f
    val glowAlpha = if (isDark) 0.16f else 0.14f
    val bodyColor = if (isDark) PwMoonlight.copy(alpha = 0.85f) else PwInkText.copy(alpha = 0.75f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, gold.copy(alpha = borderAlpha)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 6.dp else 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundBrush)
        ) {
            // Soft gold glow, purely decorative, clipped by the card's own shape.
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 50.dp, y = (-50).dp)
                    .background(gold.copy(alpha = glowAlpha), CircleShape)
            )

            Column(modifier = Modifier.padding(20.dp)) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = gold
                )
                Text(
                    text = "Play free, win real discounts",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = gold,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    text = "Pick a game below and start winning coupons in minutes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = bodyColor,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Preview
@Composable
private fun FeaturedBannerCardPreview() {
    PlayWinTheme {
        FeaturedBannerCard()
    }
}
