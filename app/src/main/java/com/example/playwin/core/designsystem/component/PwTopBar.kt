package com.example.playwin.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.example.playwin.core.designsystem.theme.PlayWinTheme

/**
 * The single top bar used across every screen in the app, so navigation
 * chrome always looks and behaves the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PwTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    titleColor: Color = Color.Unspecified,
    actions: @Composable RowScope.() -> Unit = {}
) {
    PwTopBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = titleColor
            )
        },
        modifier = modifier,
        onBack = onBack,
        actions = actions
    )
}

/** A [PwTopBar] whose title is any content, such as a logo; [height] grows the bar to fit it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PwTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    height: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    actions: @Composable RowScope.() -> Unit = {}
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = title,
        expandedHeight = height,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@Preview
@Composable
private fun PwTopBarPreview() {
    PlayWinTheme {
        PwTopBar(title = "Connect 4", onBack = {})
    }
}
