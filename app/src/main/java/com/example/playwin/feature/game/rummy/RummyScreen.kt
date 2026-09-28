package com.example.playwin.feature.game.rummy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.domain.rummy.Card
import com.example.playwin.core.domain.rummy.Rank
import com.example.playwin.core.domain.rummy.Suit
import com.example.playwin.core.domain.rummy.isWild

@Composable
fun RummyRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    viewModel: RummyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.wonCouponId) {
        uiState.wonCouponId?.let(onWin)
    }

    RummyScreen(
        uiState = uiState,
        onBack = onBack,
        onDrawFromShoe = viewModel::onDrawFromShoe,
        onDrawFromDiscard = viewModel::onDrawFromDiscard,
        onCardTap = viewModel::onCardTap,
        onConfirmDiscard = viewModel::onConfirmDiscard,
        onDeclare = viewModel::onDeclare,
        onPlayAgain = viewModel::onPlayAgain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RummyScreen(
    uiState: RummyUiState,
    onBack: () -> Unit,
    onDrawFromShoe: () -> Unit,
    onDrawFromDiscard: () -> Unit,
    onCardTap: (Card) -> Unit,
    onConfirmDiscard: () -> Unit,
    onDeclare: () -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Rummy · vs Computer", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            StatusBanner(uiState)

            Text(
                text = "Wild joker: ${rankLabel(uiState.game.wildRank)} (any suit) + printed jokers",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Deck (${uiState.game.shoe.size})", style = MaterialTheme.typography.labelMedium)
                    CardBack(
                        modifier = Modifier.padding(top = 4.dp),
                        enabled = uiState.canDraw,
                        onClick = onDrawFromShoe
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Discard", style = MaterialTheme.typography.labelMedium)
                    val top = uiState.game.discardPile.lastOrNull()
                    Box(modifier = Modifier.padding(top = 4.dp)) {
                        if (top != null) {
                            CardView(
                                card = top,
                                isWild = isWild(top, uiState.game.wildRank),
                                isSelected = false,
                                onClick = if (uiState.canDraw) onDrawFromDiscard else null
                            )
                        } else {
                            EmptyPileSlot()
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Computer's hand", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "${uiState.game.botHand.size} cards",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                    if (uiState.isBotThinking) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp).size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }

            Text(
                text = "Your hand (tap a card, then Discard or Declare)",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 20.dp)
            )
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                uiState.game.playerHand.sortedWith(compareBy({ it.sortSuit() }, { it.sortRank() })).forEach { card ->
                    CardView(
                        card = card,
                        isWild = isWild(card, uiState.game.wildRank),
                        isSelected = uiState.selectedCard?.id == card.id,
                        onClick = if (uiState.canDiscard) { { onCardTap(card) } } else null
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onConfirmDiscard,
                    enabled = uiState.canConfirmDiscard,
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("Discard")
                }
                PrimaryButton(
                    text = "Declare",
                    onClick = onDeclare,
                    enabled = uiState.canDeclare,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                )
            }

            if (uiState.isGameOver) {
                PrimaryButton(text = "Play again", onClick = onPlayAgain, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

private fun Card.sortSuit(): Int = when (this) {
    is Card.PrintedJoker -> 4
    is Card.Standard -> suit.ordinal
}

private fun Card.sortRank(): Int = when (this) {
    is Card.PrintedJoker -> 0
    is Card.Standard -> rank.order
}

@Composable
private fun StatusBanner(uiState: RummyUiState) {
    val text = when {
        uiState.hasWon -> "You win! Claiming your coupon…"
        uiState.computerDeclared -> "The computer declared a valid hand - you lose this round."
        uiState.hasLost -> "Invalid declaration - you lose this round."
        uiState.isPlayerTurn && uiState.canDraw -> "Your turn - draw a card"
        uiState.isPlayerTurn -> "Your turn - discard or declare"
        else -> "Computer's turn…"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (uiState.isResolving) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

private fun rankLabel(rank: Rank): String = when (rank) {
    Rank.ACE -> "A"
    Rank.JACK -> "J"
    Rank.QUEEN -> "Q"
    Rank.KING -> "K"
    else -> rank.order.toString()
}

private fun suitSymbol(suit: Suit): String = when (suit) {
    Suit.SPADES -> "♠"
    Suit.HEARTS -> "♥"
    Suit.DIAMONDS -> "♦"
    Suit.CLUBS -> "♣"
}

private fun suitColor(suit: Suit): Color = when (suit) {
    Suit.HEARTS, Suit.DIAMONDS -> Color(0xFFD32F2F)
    Suit.SPADES, Suit.CLUBS -> Color(0xFF1A1A1A)
}

private val CARD_WIDTH = 48.dp
private val CARD_HEIGHT = 66.dp

@Composable
private fun CardView(
    card: Card,
    isWild: Boolean,
    isSelected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val label = when (card) {
        is Card.PrintedJoker -> "JOKER"
        is Card.Standard -> "${rankLabel(card.rank)}${suitSymbol(card.suit)}"
    }
    val labelColor = when (card) {
        is Card.PrintedJoker -> MaterialTheme.colorScheme.tertiary
        is Card.Standard -> suitColor(card.suit)
    }
    val borderColor = if (isWild) Color(0xFFFFA000) else Color(0xFFBBBBBB)

    Box(
        modifier = modifier
            .size(width = CARD_WIDTH, height = CARD_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.White)
            .border(BorderStroke(if (isWild) 2.dp else 1.dp, borderColor), RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = labelColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CardBack(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = CARD_WIDTH, height = CARD_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

@Composable
private fun EmptyPileSlot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = CARD_WIDTH, height = CARD_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, Color(0xFFBBBBBB)), RoundedCornerShape(8.dp))
    )
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun RummyScreenPreview() {
    PlayWinTheme {
        RummyScreen(
            uiState = RummyUiState(),
            onBack = {},
            onDrawFromShoe = {},
            onDrawFromDiscard = {},
            onCardTap = {},
            onConfirmDiscard = {},
            onDeclare = {},
            onPlayAgain = {}
        )
    }
}
