package com.barberia.feature.auth.presentation.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.barberia.core.designsystem.component.BarberButton
import com.barberia.feature.auth.presentation.R

@Composable
fun SplashScreen(offline: Boolean = false, onRetry: () -> Unit = {}, onSignOut: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.auth_app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(32.dp))
        if (offline) {
            Text(stringResource(R.string.auth_offline), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            BarberButton(text = stringResource(R.string.auth_retry), onClick = onRetry)
            TextButton(onClick = onSignOut) { Text(stringResource(R.string.auth_sign_out)) }
        } else {
            CircularProgressIndicator()
        }
    }
}
