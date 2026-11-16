package com.barberia.core.common

import java.util.Random

/**
 * Códigos cortos para dictar o mandar por WhatsApp (código de barbería, invitación de barbero).
 * Sin 0/O ni 1/I/L para que no se confundan.
 */
object ShortCodes {
    const val LENGTH = 6
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun generate(random: Random = Random()): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** " abc-234 " → "ABC234"; null si no es un código válido. */
    fun normalize(raw: String): String? {
        val clean = raw.uppercase().filter { it.isLetterOrDigit() }
        return clean.takeIf { it.length == LENGTH && it.all { ch -> ch in ALPHABET } }
    }
}
