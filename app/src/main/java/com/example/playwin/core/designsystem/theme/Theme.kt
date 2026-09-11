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
    primary = PwViolet40,
    onPrimary = Color.White,
    primaryContainer = PwViolet80,
    onPrimaryContainer = PwViolet20,
    secondary = PwTeal40,
    onSecondary = Color.White,
    secondaryContainer = PwTeal80,
    onSecondaryContainer = PwTeal20,
    tertiary = PwGold40,
    onTertiary = Color.White,
    tertiaryContainer = PwGold80,
    onTertiaryContainer = PwGold20,
    background = PwNeutral99,
    surface = PwNeutral99,
    surfaceVariant = PwNeutral95,
    onSurfaceVariant = PwNeutral30,
    outline = PwNeutral90,
    onBackground = PwNeutral10,
    onSurface = PwNeutral10,
    error = PwError40
)

private val DarkColorScheme = darkColorScheme(
    primary = PwViolet80,
    onPrimary = PwViolet20,
    primaryContainer = PwViolet40,
    onPrimaryContainer = PwViolet80,
    secondary = PwTeal80,
    onSecondary = PwTeal20,
    secondaryContainer = PwTeal40,
    onSecondaryContainer = PwTeal80,
    tertiary = PwGold80,
    onTertiary = PwGold20,
    tertiaryContainer = PwGold40,
    onTertiaryContainer = PwGold80,
    background = PwNeutral10,
    surface = PwNeutral10,
    surfaceVariant = PwNeutral20,
    onSurfaceVariant = PwNeutral90,
    outline = PwNeutral30,
    onBackground = PwNeutral95,
    onSurface = PwNeutral95,
    error = PwError80
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
