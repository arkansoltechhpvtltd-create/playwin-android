package com.example.playwin.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.feature.game.GameRoute
import com.example.playwin.feature.game.result.GameResultRoute
import com.example.playwin.feature.lobby.LobbyRoute
import com.example.playwin.feature.profile.ProfileRoute
import com.example.playwin.feature.wallet.CouponsRoute

@Composable
fun PlayWinNavHost(
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topLevelDestinations.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Lobby.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(TopLevelDestination.Lobby.route) {
                LobbyRoute(
                    onGameClick = { gameId -> navController.navigate(Routes.game(gameId)) }
                )
            }
            composable(TopLevelDestination.Coupons.route) {
                CouponsRoute()
            }
            composable(TopLevelDestination.Profile.route) {
                ProfileRoute()
            }
            composable(Routes.GAME) { entry ->
                val gameId = entry.arguments
                    ?.getString(Routes.GAME_ARG)
                    ?.let { runCatching { GameId.valueOf(it) }.getOrNull() }
                    ?: GameId.CONNECT_FOUR

                GameRoute(
                    gameId = gameId,
                    onBack = { navController.popBackStack() },
                    onWin = { couponId ->
                        navController.navigate(Routes.gameResult(gameId, couponId)) {
                            popUpTo(Routes.game(gameId)) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.GAME_RESULT) {
                GameResultRoute(
                    onBackToGames = {
                        navController.navigate(TopLevelDestination.Lobby.route) {
                            popUpTo(TopLevelDestination.Lobby.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
