package com.barberia.feature.auth.domain.model

sealed interface SessionState {
    /** Todavía no se sabe si hay sesión. */
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: AuthUser) : SessionState

    /** Hay sesión pero no se pudo leer el perfil (sin red y sin caché). Se puede reintentar. */
    data object Unavailable : SessionState
}

enum class SessionDestination { Login, ModeSelection, OwnerHome, BarberHome, ClientHome, Offline }

/**
 * Regla única de a dónde va el usuario. `null` = todavía no se decide (se queda donde está).
 * - Cliente puro → home de cliente.
 * - Personal con un modo guardado que todavía tiene → directo a ese home.
 * - Personal sin modo (o con uno que ya no tiene) → selector.
 */
fun resolveDestination(state: SessionState, mode: AppMode?): SessionDestination? = when (state) {
    SessionState.Loading -> null

    SessionState.Unavailable -> SessionDestination.Offline

    // H2: se ve desde cualquier pantalla
    SessionState.SignedOut -> SessionDestination.Login

    is SessionState.SignedIn -> {
        val modes = state.user.availableModes
        when {
            modes == listOf(AppMode.CLIENT) -> SessionDestination.ClientHome
            mode != null && mode in modes -> mode.home()
            else -> SessionDestination.ModeSelection
        }
    }
}

private fun AppMode.home() = when (this) {
    AppMode.OWNER -> SessionDestination.OwnerHome
    AppMode.BARBER -> SessionDestination.BarberHome
    AppMode.CLIENT -> SessionDestination.ClientHome
}
