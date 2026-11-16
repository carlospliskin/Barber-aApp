package com.barberia.feature.booking.domain.model

/**
 * N1-b: número para `wa.me` (código de país + número, solo dígitos). Sin lada se asume México.
 * El mensaje lo manda el personal desde su WhatsApp: gratis y sin servidor (Spark).
 */
object WhatsApp {
    private const val DEFAULT_COUNTRY = "52"
    private const val LOCAL_DIGITS = 10
    private val INTERNATIONAL = 8..15

    fun number(phone: String): String? {
        val raw = phone.filterNot { it in " -().\t" }
        val international = raw.startsWith("+")
        val digits = raw.removePrefix("+")
        if (digits.isEmpty() || !digits.all { it.isDigit() }) return null
        return when {
            international -> digits.takeIf { it.length in INTERNATIONAL }
            digits.length == LOCAL_DIGITS -> DEFAULT_COUNTRY + digits
            digits.length == LOCAL_DIGITS + DEFAULT_COUNTRY.length && digits.startsWith(DEFAULT_COUNTRY) -> digits
            else -> null
        }
    }
}

/** N1-b: solo el cliente sin app tiene a quién avisar por WhatsApp (el de la app recibe su recordatorio). */
val Appointment.whatsAppNumber: String?
    get() = clientPhone?.takeIf { isWalkIn }?.let(WhatsApp::number)
