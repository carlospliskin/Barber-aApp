package com.barberia.feature.auth.domain.model

/** A dónde debe ir el usuario según su sesión. Regla de negocio, no de navegación. */
enum class SessionDestination { Login, ModeSelection, OwnerHome, BarberHome, ClientHome }

/**
 * - Cliente puro → home de cliente.
 * - Dueño/barbero tras login → selector de modo.
 * - Tras elegir modo → home del rol principal devuelto por el backend.
 */
fun AuthUser?.destination(afterModeSwitch: Boolean = false): SessionDestination = when {
    this == null -> SessionDestination.Login
    !isOwner && !isBarber -> SessionDestination.ClientHome
    !afterModeSwitch -> SessionDestination.ModeSelection
    primaryRole == Capability.OWNER -> SessionDestination.OwnerHome
    primaryRole == Capability.BARBER -> SessionDestination.BarberHome
    else -> SessionDestination.ClientHome
}
