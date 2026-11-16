package com.barberia.feature.booking.domain

import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.WhatsApp
import com.barberia.feature.booking.domain.model.whatsAppNumber
import com.barberia.feature.catalog.domain.model.Money
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** N1-b: avisar por WhatsApp al cliente sin app (wa.me pide el número con código de país, sin "+"). */
class WhatsAppTest {
    @Test fun `un numero de 10 digitos es de Mexico`() = assertEquals("525512345678", WhatsApp.number("5512345678"))

    @Test fun `con lada internacional se respeta`() {
        assertEquals("525512345678", WhatsApp.number("+525512345678"))
        assertEquals("12025550123", WhatsApp.number("+12025550123"))
    }

    @Test fun `espacios, guiones y parentesis se ignoran`() = assertEquals("525512345678", WhatsApp.number("(55) 1234-5678"))

    @Test fun `numeros incompletos o con letras no sirven`() {
        assertNull(WhatsApp.number("12345"))
        assertNull(WhatsApp.number("55a2345678"))
        assertNull(WhatsApp.number(""))
    }

    private fun appt(uid: String?, name: String, phone: String?) = Appointment(
        "a1", "t1", uid, name, "b1", "Luis", "s1", "Corte", Money(15_000), LocalDate.of(2026, 11, 16), 600, 630,
        AppointmentStatus.BOOKED, Instant.parse("2026-11-16T16:00:00Z"), clientPhone = phone
    )

    @Test fun `solo las citas sin app con telefono tienen WhatsApp`() {
        assertEquals("525512345678", appt(null, "Pedro", "5512345678").whatsAppNumber)
        assertNull(appt(null, "Pedro", null).whatsAppNumber)
        assertNull(appt("cli", "Ana", "5512345678").whatsAppNumber) // con app: recibe el recordatorio en el teléfono
        assertNull(appt(null, "", "5512345678").whatsAppNumber) // borró su cuenta
    }
}
