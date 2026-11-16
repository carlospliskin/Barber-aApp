package com.barberia.app

import com.barberia.app.di.toAuthFailure
import com.barberia.feature.auth.domain.model.AuthFailure
import com.barberia.feature.booking.domain.model.BookingFailure
import org.junit.Assert.assertEquals
import org.junit.Test

/** A7: el borrado de datos (booking) reporta sus fallos con los tipos de auth. */
class AccountEraserTest {
    @Test fun `sin red sigue siendo sin red`() = assertEquals(AuthFailure.NoConnection, BookingFailure.NoConnection.toAuthFailure())

    @Test fun `lo demas es desconocido con detalle`() = assertEquals(AuthFailure.Unknown("x"), BookingFailure.Unknown("x").toAuthFailure())
}
