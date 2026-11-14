package com.barberia.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.barberia.app.home.HomeRoute
import com.barberia.feature.auth.domain.model.SessionDestination
import com.barberia.feature.auth.presentation.login.LoginRoute
import com.barberia.feature.auth.presentation.mode.ModeSelectorRoute
import com.barberia.feature.auth.presentation.splash.SplashRoute
import kotlinx.serialization.Serializable

@Serializable data object SplashDest
@Serializable data object LoginDest
@Serializable data object ModeSelectionDest
@Serializable data class HomeDest(val role: String)

@Composable
fun BarberiaNavHost(navController: NavHostController = rememberNavController()) {
    val goTo: (SessionDestination) -> Unit = { navController.navigateToSession(it) }

    NavHost(navController = navController, startDestination = SplashDest) {
        composable<SplashDest> { SplashRoute(onNavigate = goTo) }
        composable<LoginDest> { LoginRoute(onNavigate = goTo) }
        composable<ModeSelectionDest> { ModeSelectorRoute(onNavigate = goTo) }
        composable<HomeDest> { entry ->
            HomeRoute(role = entry.toRoute<HomeDest>().role, onLoggedOut = { goTo(SessionDestination.Login) })
        }
    }
}

/** Traduce el destino de negocio a una ruta y limpia el back stack (no se vuelve al login con "atrás"). */
private fun NavHostController.navigateToSession(destination: SessionDestination) {
    val route: Any = when (destination) {
        SessionDestination.Login -> LoginDest
        SessionDestination.ModeSelection -> ModeSelectionDest
        SessionDestination.OwnerHome -> HomeDest("Dueño")
        SessionDestination.BarberHome -> HomeDest("Barbero")
        SessionDestination.ClientHome -> HomeDest("Cliente")
    }
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
