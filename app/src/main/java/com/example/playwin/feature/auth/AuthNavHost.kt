package com.example.playwin.feature.auth

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.playwin.feature.auth.login.LoginRoute
import com.example.playwin.feature.auth.signup.SignupRoute

private object AuthRoutes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
}

/**
 * The logged-out flow: login and signup. Neither screen navigates to the
 * lobby directly - a successful login/signup flips the shared auth state in
 * [com.example.playwin.core.domain.repository.AuthRepository], and the app
 * root swaps this whole graph out for [com.example.playwin.navigation.PlayWinNavHost].
 */
@Composable
fun AuthNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = AuthRoutes.LOGIN) {
        composable(AuthRoutes.LOGIN) {
            LoginRoute(onNavigateToSignup = { navController.navigate(AuthRoutes.SIGNUP) })
        }
        composable(AuthRoutes.SIGNUP) {
            SignupRoute(onNavigateToLogin = { navController.popBackStack() })
        }
    }
}
