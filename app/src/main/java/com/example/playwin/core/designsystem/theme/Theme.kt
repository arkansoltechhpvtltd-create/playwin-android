package com.example.playwin.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PwGoldLight,
    onPrimary = PwGoldInk,
    primaryContainer = PwGoldContainerLight,
    onPrimaryContainer = PwGoldOnContainerLight,
    secondary = PwEmeraldLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = PwEmeraldContainerLight,
    onSecondaryContainer = PwEmeraldOnContainerLight,
    tertiary = PwGoldLight,
    onTertiary = PwGoldInk,
    tertiaryContainer = PwGoldContainerLight,
    onTertiaryContainer = PwGoldOnContainerLight,
    background = PwIvory,
    surface = PwIvorySurface,
    surfaceVariant = PwIvoryRaised,
    onSurfaceVariant = PwInkMuted,
    outline = PwIvoryLine,
    onBackground = PwInkText,
    onSurface = PwInkText,
    error = PwErrorLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = PwErrorContainerLight,
    onErrorContainer = PwErrorOnContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PwGoldDark,
    onPrimary = PwGoldInk,
    primaryContainer = PwGoldContainerDark,
    onPrimaryContainer = PwGoldOnContainerDark,
    secondary = PwEmeraldDark,
    onSecondary = Color(0xFF04352A),
    secondaryContainer = PwEmeraldContainerDark,
    onSecondaryContainer = PwEmeraldOnContainerDark,
    tertiary = PwGoldDark,
    onTertiary = PwGoldInk,
    tertiaryContainer = PwGoldContainerDark,
    onTertiaryContainer = PwGoldOnContainerDark,
    background = PwInkBlack,
    surface = PwCharcoal,
    surfaceVariant = PwCharcoalRaised,
    onSurfaceVariant = PwMoonlightMuted,
    outline = PwCharcoalLine,
    onBackground = PwMoonlight,
    onSurface = PwMoonlight,
    error = PwErrorDark,
    onError = Color(0xFF601410),
    errorContainer = PwErrorContainerDark,
    onErrorContainer = PwErrorOnContainerDark
)

@Composable
fun PlayWinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PwTypography,
        shapes = PwShapes,
        content = content
    )
}
