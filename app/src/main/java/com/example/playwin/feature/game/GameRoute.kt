package com.example.playwin.feature.game

import androidx.compose.runtime.Composable
import com.example.playwin.core.domain.model.GameIconKey
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.feature.game.carrom.CarromRoute
import com.example.playwin.feature.game.comingsoon.ComingSoonRoute
import com.example.playwin.feature.game.connectfour.Connect4Route
import com.example.playwin.feature.game.eightball.EightBallRoute
import com.example.playwin.feature.game.twenty48.Twenty48Route

/**
 * Dispatches to the right game screen for a [GameId]. Connect 4, 2048,
 * 8-Ball, and Carrom are fully playable; every other title falls back to a
 * "coming soon" scaffold so navigation and the reward wiring stay consistent
 * for future games.
 */
@Composable
fun GameRoute(
    gameId: GameId,
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    when (gameId) {
        GameId.CONNECT_FOUR -> Connect4Route(onBack = onBack, onWin = onWin)
        GameId.TWENTY_FORTY_EIGHT -> Twenty48Route(onBack = onBack, onWin = onWin)
        GameId.EIGHT_BALL -> EightBallRoute(onBack = onBack, onWin = onWin)
        GameId.CARROM -> CarromRoute(onBack = onBack, onWin = onWin)
        else -> {
            val (title, icon) = gameId.displayInfo()
            ComingSoonRoute(title = title, icon = icon, onBack = onBack)
        }
    }
}

private fun GameId.displayInfo(): Pair<String, GameIconKey> = when (this) {
    GameId.TWENTY_FORTY_EIGHT -> "2048" to GameIconKey.GRID
    GameId.EIGHT_BALL -> "8-Ball" to GameIconKey.BILLIARDS
    GameId.CARROM -> "Carrom" to GameIconKey.CARROM
    GameId.CONNECT_FOUR -> "Connect 4" to GameIconKey.FOUR_IN_A_ROW
    GameId.BINGO -> "Bingo" to GameIconKey.BINGO
    GameId.WORD_HUNT -> "Word Hunt" to GameIconKey.WORD_HUNT
    GameId.CHESS -> "Chess" to GameIconKey.CHESS
    GameId.LUDO -> "Ludo" to GameIconKey.LUDO
    GameId.RUMMY -> "Rummy" to GameIconKey.CARDS
}
