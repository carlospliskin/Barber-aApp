package com.barberia.feature.catalog.presentation

import com.barberia.feature.catalog.domain.model.Money
import com.barberia.feature.catalog.domain.model.TimeRange

private val PRICE = Regex("^\\$?\\s*(\\d{1,7})(?:[.,](\\d{1,2}))?$")
private val HOUR = Regex("^(\\d{1,2}):(\\d{2})$")

/** C12: "150", "150.5", "150,50", "$19.99" → centavos. Sin negativos ni más de 2 decimales. */
fun parsePrice(text: String): Money? {
    val m = PRICE.matchEntire(text.trim()) ?: return null
    val pesos = m.groupValues[1].toLong()
    val decimals = m.groupValues[2].padEnd(2, '0').ifEmpty { "00" }.toLong()
    return Money(pesos * 100 + decimals)
}

fun parseMinutes(text: String): Int? = text.trim().toIntOrNull()

/** "9:00" o "09:00" → minutos del día. */
fun parseHour(text: String): Int? {
    val m = HOUR.matchEntire(text.trim()) ?: return null
    val (h, min) = m.destructured
    val hour = h.toInt()
    val minute = min.toInt()
    if (hour > 24 || minute > 59 || (hour == 24 && minute != 0)) return null
    return hour * 60 + minute
}

fun Money.toInput(): String = "%d.%02d".format(cents / 100, cents % 100).removeSuffix(".00")

fun minuteLabel(minute: Int) = TimeRange.label(minute)

private val DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")
    .withResolverStyle(java.time.format.ResolverStyle.STRICT)

/** N2: "24/12/2026" → fecha (estricto: 31/02 no existe). */
fun parseDate(text: String): java.time.LocalDate? = runCatching { java.time.LocalDate.parse(text.trim(), DATE_FORMAT) }.getOrNull()

fun java.time.LocalDate.toInput(): String = format(DATE_FORMAT)
