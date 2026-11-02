package com.example.playwin.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.example.playwin.core.designsystem.theme.PwSpacing
import com.example.playwin.core.domain.model.SUPPORT_EMAIL

/** Tells a self-excluded player where they stand. There's no way to lift it in the app, only this route to support. */
@Composable
fun SelfExcludedNotice(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier) {
        Text(text = "You're excluded from all games", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "You chose to block this account from playing. To ask about coming back, contact support.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = PwSpacing.xs)
        )
        // With no mail app the tap does nothing, but the address is still there to read.
        TextButton(onClick = { runCatching { uriHandler.openUri("mailto:$SUPPORT_EMAIL") } }) {
            Text(SUPPORT_EMAIL)
        }
    }
}
