package com.example.playwin.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTextField
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.component.SectionHeader
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwSpacing

@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(
        uiState = uiState,
        onEditNameClick = viewModel::onEditNameClick,
        onEditedNameChange = viewModel::onEditedNameChange,
        onSaveNameClick = viewModel::onSaveNameClick,
        onCancelEditName = viewModel::onCancelEditName,
        onDarkModeChange = viewModel::onDarkModeChange,
        onNotificationsChange = viewModel::onNotificationsChange,
        onSoundChange = viewModel::onSoundChange,
        onDailyLimitChange = viewModel::onDailyLimitChange,
        onSelfExclusionChange = viewModel::onSelfExclusionChange,
        // Logging out flips the shared auth state; the app root reacts by
        // swapping this whole graph out for the login screen.
        onLogoutClick = viewModel::onLogoutClick,
        onCreateAccountClick = viewModel::onCreateAccountClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onEditNameClick: () -> Unit,
    onEditedNameChange: (String) -> Unit,
    onSaveNameClick: () -> Unit,
    onCancelEditName: () -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onDailyLimitChange: (Int) -> Unit,
    onSelfExclusionChange: (Boolean) -> Unit,
    onLogoutClick: () -> Unit,
    onCreateAccountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Profile") }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = PwSpacing.md)
                .padding(bottom = PwSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfileHeader(
                uiState = uiState,
                onEditNameClick = onEditNameClick,
                onEditedNameChange = onEditedNameChange,
                onSaveNameClick = onSaveNameClick,
                onCancelEditName = onCancelEditName
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PwSpacing.xl)
            ) {
                SectionHeader(title = "Account")
                if (uiState.isGuest) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        ListItem(
                            leadingContent = { Icon(imageVector = Icons.Filled.Person, contentDescription = null) },
                            headlineContent = { Text("Guest") },
                            supportingContent = { Text("You're browsing without an account") },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    Card(
                        onClick = onCreateAccountClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = PwSpacing.sm),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "Create an account",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            supportingContent = { Text("Save your progress and coupons") },
                            trailingContent = { Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        ListItem(
                            leadingContent = { Icon(imageVector = Icons.Filled.Shield, contentDescription = null) },
                            headlineContent = { Text("Email or phone") },
                            trailingContent = {
                                Text(text = uiState.identifier, style = MaterialTheme.typography.bodyMedium)
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PwSpacing.lg)
            ) {
                SectionHeader(title = "Preferences")
                Column(verticalArrangement = Arrangement.spacedBy(PwSpacing.sm)) {
                    SettingSwitchRow(
                        icon = Icons.Filled.DarkMode,
                        title = "Dark mode",
                        checked = uiState.darkModeEnabled,
                        onCheckedChange = onDarkModeChange
                    )
                    SettingSwitchRow(
                        icon = Icons.Filled.NotificationsNone,
                        title = "Notifications",
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = onNotificationsChange
                    )
                    SettingSwitchRow(
                        icon = Icons.Filled.VolumeUp,
                        title = "Sound",
                        checked = uiState.soundEnabled,
                        onCheckedChange = onSoundChange
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PwSpacing.lg)
            ) {
                SectionHeader(title = "Responsible gaming")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(PwSpacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row {
                                Icon(imageVector = Icons.Filled.TrendingUp, contentDescription = null)
                                Text(
                                    text = "Playtime today",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = PwSpacing.sm)
                                )
                            }
                            Text(
                                text = "${uiState.playtimeTodayMinutes} min",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DailyLimitDropdown(
                            selectedMinutes = uiState.dailyLimitMinutes,
                            onSelect = onDailyLimitChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = PwSpacing.md)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = PwSpacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Self-exclusion", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "Temporarily block yourself from playing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = uiState.selfExclusionEnabled, onCheckedChange = onSelfExclusionChange)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PwSpacing.lg)
            ) {
                SectionHeader(title = "Support")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    ListItem(
                        leadingContent = { Icon(imageVector = Icons.Filled.HelpOutline, contentDescription = null) },
                        headlineContent = { Text("Help & grievance") },
                        trailingContent = { Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }

            PrimaryButton(
                text = "Log out",
                onClick = onLogoutClick,
                icon = Icons.Filled.Logout,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PwSpacing.xl, bottom = PwSpacing.lg)
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    uiState: ProfileUiState,
    onEditNameClick: () -> Unit,
    onEditedNameChange: (String) -> Unit,
    onSaveNameClick: () -> Unit,
    onCancelEditName: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = PwSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = uiState.initials,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        if (uiState.isEditingName) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PwSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PwTextField(
                    value = uiState.editedName,
                    onValueChange = onEditedNameChange,
                    label = "Display name",
                    errorText = uiState.nameError
                )
                Row(modifier = Modifier.padding(top = PwSpacing.sm)) {
                    IconButton(onClick = onCancelEditName) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Cancel")
                    }
                    PrimaryButton(
                        text = "Save",
                        onClick = onSaveNameClick,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 24.dp,
                            vertical = 10.dp
                        )
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = PwSpacing.sm)
            ) {
                Text(
                    text = uiState.displayName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onEditNameClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit display name",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** Preset daily-limit options, in minutes. 0 means "No limit". */
private val DAILY_LIMIT_OPTIONS = listOf(30, 60, 90, 120, 180, 0)

private fun dailyLimitLabel(minutes: Int): String = if (minutes <= 0) "No limit" else "$minutes min"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyLimitDropdown(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = dailyLimitLabel(selectedMinutes),
            onValueChange = {},
            readOnly = true,
            label = { Text("Daily limit") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Timer, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DAILY_LIMIT_OPTIONS.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(dailyLimitLabel(minutes)) },
                    onClick = {
                        onSelect(minutes)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        ListItem(
            leadingContent = { Icon(imageVector = icon, contentDescription = null) },
            headlineContent = { Text(title) },
            trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Preview
@Composable
private fun ProfileScreenPreview() {
    PlayWinTheme {
        ProfileScreen(
            uiState = ProfileUiState(
                displayName = "Arka Sol",
                initials = "AS",
                identifier = "demo@playwin.app",
                dailyLimitMinutes = 120,
                playtimeTodayMinutes = 42
            ),
            onEditNameClick = {},
            onEditedNameChange = {},
            onSaveNameClick = {},
            onCancelEditName = {},
            onDarkModeChange = {},
            onNotificationsChange = {},
            onSoundChange = {},
            onDailyLimitChange = {},
            onSelfExclusionChange = {},
            onLogoutClick = {},
            onCreateAccountClick = {}
        )
    }
}
