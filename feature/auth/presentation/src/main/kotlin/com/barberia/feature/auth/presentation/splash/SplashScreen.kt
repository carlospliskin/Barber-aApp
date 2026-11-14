package com.barberia.feature.auth.presentation.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import com.barberia.feature.auth.domain.model.SessionDestination
import org.koin.androidx.compose.koinViewModel

@Composable
fun SplashRoute(
    onNavigate: (SessionDestination) -> Unit,
    viewModel: SplashViewModel = koinViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.onIntent(SplashIntent.Start)
        viewModel.effects.collect { effect ->
            when (effect) {
                is SplashEffect.Navigate -> onNavigate(effect.destination)
            }
        }
    }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
