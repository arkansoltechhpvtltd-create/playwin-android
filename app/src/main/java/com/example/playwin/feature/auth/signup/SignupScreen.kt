package com.example.playwin.feature.auth.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.core.designsystem.component.PrimaryButton
import com.example.playwin.core.designsystem.component.PwTextField
import com.example.playwin.core.designsystem.component.PwTopBar
import com.example.playwin.core.designsystem.theme.PlayWinTheme
import com.example.playwin.core.designsystem.theme.PwSpacing
import com.example.playwin.feature.auth.AuthHeader

@Composable
fun SignupRoute(
    onNavigateToLogin: () -> Unit,
    viewModel: SignupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SignupScreen(
        uiState = uiState,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onIdentifierChange = viewModel::onIdentifierChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onIsAdultChange = viewModel::onIsAdultChange,
        onCreateAccountClick = viewModel::onCreateAccountClick,
        onBack = onNavigateToLogin
    )
}

@Composable
fun SignupScreen(
    uiState: SignupUiState,
    onDisplayNameChange: (String) -> Unit,
    onIdentifierChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onIsAdultChange: (Boolean) -> Unit,
    onCreateAccountClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { PwTopBar(title = "Create account", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = PwSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AuthHeader(
                subtitle = "Join in seconds and start winning",
                modifier = Modifier.padding(top = PwSpacing.lg)
            )

            Column(modifier = Modifier.padding(top = PwSpacing.xl, bottom = PwSpacing.lg)) {
                PwTextField(
                    value = uiState.displayName,
                    onValueChange = onDisplayNameChange,
                    label = "Display name",
                    errorText = uiState.displayNameError
                )

                PwTextField(
                    value = uiState.identifier,
                    onValueChange = onIdentifierChange,
                    label = "Email or phone number",
                    errorText = uiState.identifierError,
                    keyboardType = KeyboardType.Email,
                    modifier = Modifier.padding(top = PwSpacing.sm)
                )

                PwTextField(
                    value = uiState.password,
                    onValueChange = onPasswordChange,
                    label = "Password",
                    errorText = uiState.passwordError,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.padding(top = PwSpacing.sm)
                )

                PwTextField(
                    value = uiState.confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    label = "Confirm password",
                    errorText = uiState.confirmPasswordError,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.padding(top = PwSpacing.sm)
                )

                Column(modifier = Modifier.padding(top = PwSpacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uiState.isAdult, onCheckedChange = onIsAdultChange)
                        Text(
                            text = "I am 18 or older",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (uiState.ageError != null) {
                        Text(
                            text = uiState.ageError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = PwSpacing.md)
                        )
                    }
                }

                if (uiState.generalError != null) {
                    Text(
                        text = uiState.generalError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = PwSpacing.sm)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = PwSpacing.lg),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.padding(vertical = 14.dp))
                    } else {
                        PrimaryButton(
                            text = "Create account",
                            onClick = onCreateAccountClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun SignupScreenPreview() {
    PlayWinTheme {
        SignupScreen(
            uiState = SignupUiState(),
            onDisplayNameChange = {},
            onIdentifierChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onIsAdultChange = {},
            onCreateAccountClick = {},
            onBack = {}
        )
    }
}
