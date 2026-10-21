package com.example.playwin.feature.game

import androidx.compose.runtime.Composable
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.feature.game.sudoku.SudokuRoute
import com.example.playwin.feature.game.carrom.CarromRoute
import com.example.playwin.feature.game.chess.ChessRoute
import com.example.playwin.feature.game.chinesecheckers.ChineseCheckersRoute
import com.example.playwin.feature.game.eightball.EightBallRoute
import com.example.playwin.feature.game.ludo.LudoRoute
import com.example.playwin.feature.game.rummy.RummyRoute
import com.example.playwin.feature.game.twenty48.Twenty48Route
import com.example.playwin.feature.game.wordhunt.WordHuntRoute

/** Dispatches to the right game screen for a [GameId]. Every game is fully playable. */
@Composable
fun GameRoute(
    gameId: GameId,
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit
) {
    when (gameId) {
        GameId.CHINESE_CHECKERS -> ChineseCheckersRoute(onBack = onBack, onWin = onWin)
        GameId.TWENTY_FORTY_EIGHT -> Twenty48Route(onBack = onBack, onWin = onWin)
        GameId.EIGHT_BALL -> EightBallRoute(onBack = onBack, onWin = onWin)
        GameId.CARROM -> CarromRoute(onBack = onBack, onWin = onWin)
        GameId.SUDOKU -> SudokuRoute(onBack = onBack, onWin = onWin)
        GameId.WORD_HUNT -> WordHuntRoute(onBack = onBack, onWin = onWin)
        GameId.CHESS -> ChessRoute(onBack = onBack, onWin = onWin)
        GameId.LUDO -> LudoRoute(onBack = onBack, onWin = onWin)
        GameId.RUMMY -> RummyRoute(onBack = onBack, onWin = onWin)
    }
}
