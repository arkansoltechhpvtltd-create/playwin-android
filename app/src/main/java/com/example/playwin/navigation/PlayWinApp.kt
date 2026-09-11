package com.example.playwin.navigation

import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.feature.auth.AuthNavHost

/**
 * The app root: picks the theme from settings and swaps between the logged-out
 * (auth) graph and the main app graph based on [SessionViewModel.authUser].
 * This is the one place auth state drives navigation - individual screens
 * never need to know about each other's routes.
 */
@Composable
fun PlayWinApp(
    sessionViewModel: SessionViewModel = hiltViewModel()
) {
    val authUser by sessionViewModel.authUser.collectAsStateWithLifecycle()
    val settings by sessionViewModel.settings.collectAsStateWithLifecycle()
    val darkTheme = settings.darkModeEnabled

    // enableEdgeToEdge() in MainActivity only sets the status/navigation bar
    // icon style once, based on the *system* theme at launch. Our dark mode is
    // an independent, in-app toggle, so without this the system bar icons can
    // end up light-on-light or dark-on-dark whenever the two disagree. Keep
    // them in sync with what's actually on screen every time it changes.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }

    PlayWinTheme(darkTheme = darkTheme) {
        Crossfade(targetState = authUser != null, label = "authGate") { isLoggedIn ->
            if (isLoggedIn) {
                PlayWinNavHost()
            } else {
                AuthNavHost()
            }
        }
    }
}
