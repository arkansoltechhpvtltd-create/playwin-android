package com.example.playwin.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.playwin.core.domain.model.GameId

/** The three bottom-navigation destinations. */
sealed class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Lobby : TopLevelDestination("lobby", "Games", Icons.Filled.SportsEsports)
    data object Coupons : TopLevelDestination("coupons", "Coupons", Icons.Filled.ConfirmationNumber)
    data object Profile : TopLevelDestination("profile", "Profile", Icons.Filled.Person)
}

val topLevelDestinations = listOf(
    TopLevelDestination.Lobby,
    TopLevelDestination.Coupons,
    TopLevelDestination.Profile
)

/** Routes reached by navigating away from the bottom-nav tabs. */
object Routes {
    const val GAME_ARG = "gameId"
    const val COUPON_ARG = "couponId"

    const val GAME = "game/{$GAME_ARG}"
    const val GAME_RESULT = "game_result/{$GAME_ARG}/{$COUPON_ARG}"

    fun game(gameId: GameId) = "game/${gameId.name}"
    fun gameResult(gameId: GameId, couponId: String) = "game_result/${gameId.name}/$couponId"
}
