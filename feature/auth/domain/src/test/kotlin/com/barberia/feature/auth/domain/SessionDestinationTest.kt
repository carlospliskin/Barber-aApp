package com.barberia.feature.auth.domain

import com.barberia.feature.auth.domain.model.AuthUser
import com.barberia.feature.auth.domain.model.Capability
import com.barberia.feature.auth.domain.model.SessionDestination
import com.barberia.feature.auth.domain.model.destination
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDestinationTest {
    private fun user(vararg caps: Capability, primary: Capability = caps.first()) =
        AuthUser("user-123456789", "t", "r", caps.toSet(), primary)

    @Test fun `sin usuario va a login`() =
        assertEquals(SessionDestination.Login, (null as AuthUser?).destination())

    @Test fun `cliente puro va directo a su home`() =
        assertEquals(SessionDestination.ClientHome, user(Capability.CLIENT).destination())

    @Test fun `duenio tras login elige modo`() =
        assertEquals(SessionDestination.ModeSelection, user(Capability.OWNER, Capability.CLIENT).destination())

    @Test fun `tras cambiar de modo va al rol principal`() {
        assertEquals(SessionDestination.OwnerHome, user(Capability.OWNER).destination(afterModeSwitch = true))
        assertEquals(
            SessionDestination.BarberHome,
            user(Capability.OWNER, Capability.BARBER, primary = Capability.BARBER).destination(afterModeSwitch = true),
        )
    }

    @Test fun `iniciales y nombre`() {
        val u = user(Capability.CLIENT).copy(
            clientProfile = com.barberia.feature.auth.domain.model.ClientProfileInfo("c", "juan perez"),
        )
        assertEquals("juan perez", u.displayName)
        assertEquals("JP", u.initials)
        assertEquals("U", user(Capability.CLIENT).copy(userId = "").initials)
    }
}
