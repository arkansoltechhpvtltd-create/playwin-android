package com.example.playwin.feature.lobby

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.CouponBalanceChip
import com.example.playwin.core.designsystem.component.FeaturedBannerCard
import com.example.playwin.core.designsystem.component.GameTileCard
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.component.SectionHeader
import com.example.playwin.core.designsystem.icon.toAccentColor
import com.example.playwin.core.designsystem.icon.toImageVector
import com.example.playwin.core.designsystem.theme.PwSpacing
import com.example.playwin.core.domain.model.GameId

@Composable
fun LobbyRoute(
    onGameClick: (GameId) -> Unit,
    viewModel: LobbyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LobbyScreen(uiState = uiState, onGameClick = onGameClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    uiState: LobbyUiState,
    onGameClick: (GameId) -> Unit,
    modifier: Modifier = Modifier
) {
    val fullSpan: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope) -> GridItemSpan =
        { GridItemSpan(3) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "PlayWin") }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(PwSpacing.sm),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(PwSpacing.sm),
            contentPadding = PaddingValues(
                start = PwSpacing.md,
                end = PwSpacing.md,
                top = innerPadding.calculateTopPadding() + PwSpacing.xs,
                bottom = PwSpacing.lg
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = fullSpan) {
                Text(
                    text = "Free to play · win coupons",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = PwSpacing.md)
                )
            }
            item(span = fullSpan) {
                CouponBalanceChip(
                    count = uiState.couponBalance,
                    modifier = Modifier.padding(bottom = PwSpacing.md)
                )
            }
            item(span = fullSpan) {
                FeaturedBannerCard(modifier = Modifier.padding(bottom = PwSpacing.lg))
            }
            item(span = fullSpan) {
                SectionHeader(title = "Games")
            }
            items(uiState.games) { game ->
                GameTileCard(
                    title = game.title,
                    icon = game.icon.toImageVector(),
                    accentColor = game.icon.toAccentColor(),
                    onClick = { onGameClick(game.id) }
                )
            }
        }
    }
}
