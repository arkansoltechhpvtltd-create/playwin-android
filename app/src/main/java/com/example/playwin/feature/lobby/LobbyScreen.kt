package com.example.playwin.feature.lobby

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.playwin.core.designsystem.image.artworkPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.R
import com.example.playwin.core.designsystem.component.CouponBalanceChip
import com.example.playwin.core.designsystem.component.FeaturedBannerCard
import com.example.playwin.core.designsystem.component.GameTileCard
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.component.SectionHeader
import com.example.playwin.core.designsystem.component.SelfExcludedNotice
import com.example.playwin.core.designsystem.icon.toBadgePainter
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
        topBar = {
            PwTopBar(
                title = {
                    Image(
                        painter = artworkPainter(R.drawable.playwin_logo),
                        contentDescription = "PlayWin",
                        modifier = Modifier.size(84.dp)
                    )
                },
                height = 96.dp
            )
        }
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
            if (uiState.isSelfExcluded) {
                item(span = fullSpan) {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.padding(bottom = PwSpacing.lg)
                    ) {
                        SelfExcludedNotice(modifier = Modifier.padding(PwSpacing.md))
                    }
                }
            }
            item(span = fullSpan) {
                SectionHeader(title = "Games")
            }
            if (uiState.isLoading) {
                items(SKELETON_TILE_COUNT) {
                    GameTileSkeleton()
                }
            } else {
                items(uiState.games, key = { it.id }) { game ->
                    GameTileCard(
                        title = game.title,
                        icon = game.icon.toImageVector(),
                        accentColor = game.icon.toAccentColor(),
                        image = game.icon.toBadgePainter(),
                        // The only way into a game, vs Computer or online.
                        onClick = { if (!uiState.isSelfExcluded) onGameClick(game.id) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

private const val SKELETON_TILE_COUNT = 9

/** A grey stand-in the size and shape of a [GameTileCard], gently breathing while the games load. */
@Composable
private fun GameTileSkeleton(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "lobbySkeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "lobbySkeletonPulse"
    )
    Box(
        modifier = modifier
            .aspectRatio(0.95f)
            .graphicsLayer { alpha = pulse }
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large)
    )
}
