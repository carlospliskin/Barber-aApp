package com.barberia.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.barberia.app.home.HomeRoute
import com.barberia.feature.auth.domain.model.AppMode
import com.barberia.feature.auth.domain.model.SessionDestination
import com.barberia.feature.auth.presentation.login.LoginRoute
import com.barberia.feature.auth.presentation.mode.ModeSelectorRoute
import com.barberia.feature.auth.presentation.register.RegisterRoute
import com.barberia.feature.auth.presentation.session.SessionIntent
import com.barberia.feature.auth.presentation.session.SessionViewModel
import com.barberia.feature.auth.presentation.session.SplashScreen
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Serializable data object SplashDest
@Serializable data object LoginDest
@Serializable data object RegisterDest
@Serializable data object ModeSelectionDest
@Serializable data class HomeDest(val mode: AppMode)

/**
 * La sesión manda: cuando cambia el destino (login, registro, elegir modo, cerrar sesión, sesión
 * restaurada tras la muerte del proceso) se navega y se limpia el back stack.
 */
@Composable
fun BarberiaNavHost(
    navController: NavHostController = rememberNavController(),
    session: SessionViewModel = koinViewModel(),
) {
    val state by session.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let { navController.navigateToSession(it) }
    }

    NavHost(navController = navController, startDestination = SplashDest) {
        composable<SplashDest> {
            SplashScreen(showRetry = state.showRetry, onRetry = { session.onIntent(SessionIntent.Retry) })
        }
        composable<LoginDest> { LoginRoute(onOpenRegister = { navController.navigate(RegisterDest) }) }
        composable<RegisterDest> { RegisterRoute(onClose = { navController.popBackStack() }) }
        composable<ModeSelectionDest> { ModeSelectorRoute() }
        composable<HomeDest> { entry -> HomeRoute(mode = entry.toRoute<HomeDest>().mode) }
    }
}

private fun NavHostController.navigateToSession(destination: SessionDestination) {
    val route: Any = when (destination) {
        SessionDestination.Login -> LoginDest
        SessionDestination.ModeSelection -> ModeSelectionDest
        SessionDestination.OwnerHome -> HomeDest(AppMode.OWNER)
        SessionDestination.BarberHome -> HomeDest(AppMode.BARBER)
        SessionDestination.ClientHome -> HomeDest(AppMode.CLIENT)
    }
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
