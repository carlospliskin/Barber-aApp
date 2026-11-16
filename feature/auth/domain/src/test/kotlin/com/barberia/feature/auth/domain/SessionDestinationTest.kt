package com.barberia.feature.auth.domain

import com.barberia.feature.auth.domain.model.AppMode
import com.barberia.feature.auth.domain.model.SessionDestination
import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.model.resolveDestination
import com.barberia.feature.auth.domain.testing.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionDestinationTest {

    @Test fun `cargando no decide destino (se queda en splash)`() = assertNull(resolveDestination(SessionState.Loading, null))

    @Test fun `H2 sin perfil por falta de red va a la pantalla sin conexion desde cualquier lugar`() =
        assertEquals(SessionDestination.Offline, resolveDestination(SessionState.Unavailable, AppMode.OWNER))

    @Test fun `sin sesion va a login aunque haya un modo guardado`() =
        assertEquals(SessionDestination.Login, resolveDestination(SessionState.SignedOut, AppMode.OWNER))

    @Test fun `cliente puro siempre va a su home`() =
        assertEquals(SessionDestination.ClientHome, resolveDestination(SessionState.SignedIn(user()), null))

    @Test fun `staff sin modo elegido va al selector`() =
        assertEquals(SessionDestination.ModeSelection, resolveDestination(SessionState.SignedIn(user(StaffRole.OWNER)), null))

    @Test fun `staff con modo valido va directo al home de ese modo (A4 sobrevive reinicio)`() {
        val owner = SessionState.SignedIn(user(StaffRole.OWNER))
        assertEquals(SessionDestination.OwnerHome, resolveDestination(owner, AppMode.OWNER))
        assertEquals(SessionDestination.ModeSelection, resolveDestination(owner, AppMode.BARBER)) // W1: no ligado
        assertEquals(SessionDestination.ClientHome, resolveDestination(owner, AppMode.CLIENT))
    }

    @Test fun `modo guardado que el usuario ya no tiene vuelve al selector`() = assertEquals(
        SessionDestination.ModeSelection,
        resolveDestination(SessionState.SignedIn(user(StaffRole.BARBER)), AppMode.OWNER)
    )

    @Test fun `modos disponibles por rol (A6 todo staff tambien es cliente)`() {
        // W1: el dueño solo tiene modo Barbero si está ligado a un barbero del catálogo
        assertEquals(listOf(AppMode.OWNER, AppMode.CLIENT), user(StaffRole.OWNER).availableModes)
        assertEquals(
            listOf(AppMode.OWNER, AppMode.BARBER, AppMode.CLIENT),
            user(StaffRole.OWNER).copy(staffBarberId = "b1").availableModes
        )
        assertEquals(listOf(AppMode.BARBER, AppMode.CLIENT), user(StaffRole.BARBER).availableModes)
        assertEquals(listOf(AppMode.CLIENT), user().availableModes)
    }

    @Test fun `iniciales`() {
        assertEquals("AP", user().initials)
        assertEquals("L", user(name = "luis").initials)
        assertEquals("?", user(name = "  ").initials)
    }
}
