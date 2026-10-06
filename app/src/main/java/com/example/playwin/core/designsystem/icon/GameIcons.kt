package com.example.playwin.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.example.playwin.R
import com.example.playwin.core.designsystem.theme.PwGameBilliards
import com.example.playwin.core.designsystem.theme.PwGameBingo
import com.example.playwin.core.designsystem.theme.PwGameCards
import com.example.playwin.core.designsystem.theme.PwGameCarrom
import com.example.playwin.core.designsystem.theme.PwGameChess
import com.example.playwin.core.designsystem.theme.PwGameFourInARow
import com.example.playwin.core.designsystem.theme.PwGameGrid
import com.example.playwin.core.designsystem.theme.PwGameLudo
import com.example.playwin.core.designsystem.theme.PwGameWordHunt
import com.example.playwin.core.domain.model.GameIconKey

/**
 * Maps the UI-framework-agnostic [GameIconKey] to a concrete Material icon.
 * Keeping this mapping in the designsystem layer keeps core.domain free of
 * any Compose dependency.
 */
fun GameIconKey.toImageVector(): ImageVector = when (this) {
    GameIconKey.GRID -> Icons.Filled.GridOn
    GameIconKey.BILLIARDS -> Icons.Filled.Circle
    GameIconKey.CARROM -> Icons.Filled.ViewModule
    GameIconKey.FOUR_IN_A_ROW -> Icons.Filled.SportsEsports
    GameIconKey.BINGO -> Icons.Filled.Casino
    GameIconKey.WORD_HUNT -> Icons.Filled.TextFields
    GameIconKey.CHESS -> Icons.Filled.Extension
    GameIconKey.LUDO -> Icons.Filled.EmojiEvents
    GameIconKey.CARDS -> Icons.Filled.Style
}

/** A distinct accent colour per game so the lobby grid reads as a set of games, not one template. */
fun GameIconKey.toAccentColor(): Color = when (this) {
    GameIconKey.GRID -> PwGameGrid
    GameIconKey.BILLIARDS -> PwGameBilliards
    GameIconKey.CARROM -> PwGameCarrom
    GameIconKey.FOUR_IN_A_ROW -> PwGameFourInARow
    GameIconKey.BINGO -> PwGameBingo
    GameIconKey.WORD_HUNT -> PwGameWordHunt
    GameIconKey.CHESS -> PwGameChess
    GameIconKey.LUDO -> PwGameLudo
    GameIconKey.CARDS -> PwGameCards
}

/** Artwork that replaces the icon badge on a game's lobby tile, for games that have it. */
@Composable
fun GameIconKey.toBadgePainter(): Painter? = when (this) {
    GameIconKey.LUDO -> painterResource(R.drawable.ludo_logo)
    else -> null
}
