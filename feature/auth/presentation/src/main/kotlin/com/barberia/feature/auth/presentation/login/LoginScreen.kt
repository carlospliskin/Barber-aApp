package com.barberia.feature.auth.presentation.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barberia.core.designsystem.component.BarberButton
import com.barberia.core.designsystem.component.BarberTextField
import com.barberia.core.designsystem.theme.BarberTheme
import com.barberia.core.mvi.CollectEffects
import com.barberia.feature.auth.domain.model.FieldError
import com.barberia.feature.auth.presentation.R
import com.barberia.feature.auth.presentation.common.messageRes
import com.barberia.feature.auth.presentation.common.textFor
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginRoute(onOpenRegister: () -> Unit, viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            LoginEffect.OpenRegister -> onOpenRegister()

            LoginEffect.PasswordResetSent ->
                scope.launch { snackbar.showSnackbar(context.getString(R.string.auth_reset_sent)) }
        }
    }
    Box(Modifier.fillMaxSize()) {
        LoginScreen(state = state, onIntent = viewModel::onIntent)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).systemBarsPadding())
    }
}

@Composable
fun LoginScreen(state: LoginState, onIntent: (LoginIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            stringResource(R.string.auth_app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.auth_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(40.dp))
        BarberTextField(
            value = state.email,
            onValueChange = { onIntent(LoginIntent.EmailChanged(it)) },
            label = stringResource(R.string.auth_email),
            keyboardType = KeyboardType.Email,
            enabled = !state.isLoading,
            error = state.fieldErrors.textFor(FieldError.EMAIL_INVALID)
        )
        Spacer(Modifier.height(12.dp))
        BarberTextField(
            value = state.password,
            onValueChange = { onIntent(LoginIntent.PasswordChanged(it)) },
            label = stringResource(R.string.auth_password),
            isPassword = true,
            enabled = !state.isLoading,
            error = state.fieldErrors.textFor(FieldError.PASSWORD_EMPTY)
        )
        TextButton(
            onClick = { onIntent(LoginIntent.ForgotPassword) },
            modifier = Modifier.align(Alignment.End)
        ) { Text(stringResource(R.string.auth_forgot_password)) }
        state.failure?.let {
            Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(12.dp))
        BarberButton(
            text = stringResource(R.string.auth_sign_in),
            onClick = { onIntent(LoginIntent.Submit) },
            isLoading = state.isLoading
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { onIntent(LoginIntent.GoToRegister) }) {
            Text(stringResource(R.string.auth_no_account))
        }
    }
}

@Preview
@Composable
private fun LoginPreview() = BarberTheme {
    LoginScreen(LoginState(email = "ana@", fieldErrors = setOf(FieldError.EMAIL_INVALID))) {}
}
