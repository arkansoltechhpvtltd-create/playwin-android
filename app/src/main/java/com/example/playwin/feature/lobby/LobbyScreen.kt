package com.example.playwin.feature.lobby

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.CouponBalanceChip
import com.example.playwin.core.designsystem.component.FeaturedBannerCard
import com.example.playwin.core.designsystem.component.GameTileCard
import com.example.playwin.core.designsystem.icon.toImageVector
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
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(text = "PlayWin", style = MaterialTheme.typography.titleLarge)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = 24.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = fullSpan) {
                Text(
                    text = "Free to play · win coupons",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            item(span = fullSpan) {
                CouponBalanceChip(
                    count = uiState.couponBalance,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            item(span = fullSpan) {
                FeaturedBannerCard(modifier = Modifier.padding(bottom = 20.dp))
            }
            items(uiState.games) { game ->
                GameTileCard(
                    title = game.title,
                    icon = game.icon.toImageVector(),
                    onClick = { onGameClick(game.id) },
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
    }
}
