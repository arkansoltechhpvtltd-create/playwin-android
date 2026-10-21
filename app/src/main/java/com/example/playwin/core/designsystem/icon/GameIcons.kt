package com.example.playwin.core.designsystem.icon

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.playwin.R
import com.example.playwin.core.designsystem.image.artworkPainter
import com.example.playwin.core.designsystem.theme.PwGameBilliards
import com.example.playwin.core.designsystem.theme.PwGameSudoku
import com.example.playwin.core.designsystem.theme.PwGameCards
import com.example.playwin.core.designsystem.theme.PwGameCarrom
import com.example.playwin.core.designsystem.theme.PwGameChess
import com.example.playwin.core.designsystem.theme.PwGameChineseCheckers
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
    GameIconKey.CHINESE_CHECKERS -> Icons.Filled.Hexagon
    GameIconKey.SUDOKU -> Icons.Filled.Apps
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
    GameIconKey.CHINESE_CHECKERS -> PwGameChineseCheckers
    GameIconKey.SUDOKU -> PwGameSudoku
    GameIconKey.WORD_HUNT -> PwGameWordHunt
    GameIconKey.CHESS -> PwGameChess
    GameIconKey.LUDO -> PwGameLudo
    GameIconKey.CARDS -> PwGameCards
}

/** The badge artwork drawable for a game's lobby tile, for games that have it. */
@DrawableRes
fun GameIconKey.badgeRes(): Int? = when (this) {
    GameIconKey.GRID -> R.drawable.twozerofoureight_logo
    GameIconKey.BILLIARDS -> R.drawable.eightball_logo
    GameIconKey.CARROM -> R.drawable.carrom_logo
    GameIconKey.CHINESE_CHECKERS -> R.drawable.chinesecheckers_logo
    GameIconKey.SUDOKU -> R.drawable.sudoku_logo
    GameIconKey.WORD_HUNT -> R.drawable.wordhunt_logo
    GameIconKey.CHESS -> R.drawable.chess_logo
    GameIconKey.LUDO -> R.drawable.ludo_logo
    GameIconKey.CARDS -> R.drawable.rummy_logo
}

/** Artwork that replaces the icon badge on a game's lobby tile, for games that have it. */
@Composable
fun GameIconKey.toBadgePainter(): Painter? = badgeRes()?.let { artworkPainter(it) }

/** Every image the lobby draws, so it can be decoded before the lobby first appears. */
val LobbyArtwork: List<Int> = GameIconKey.entries.mapNotNull { it.badgeRes() } + R.drawable.playwin_logo
