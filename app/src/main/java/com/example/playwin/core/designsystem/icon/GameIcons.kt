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
import androidx.compose.ui.graphics.vector.ImageVector
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
