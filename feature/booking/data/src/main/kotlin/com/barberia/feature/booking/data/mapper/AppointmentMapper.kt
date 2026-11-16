package com.barberia.feature.booking.data.mapper

import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.dayId
import com.barberia.feature.catalog.domain.model.Money
import java.time.Instant
import java.time.LocalDate

/** Cita ↔ mapa con exactamente los campos que exigen las reglas (`slotKeys`, `dayId`, `startsAt`). */
internal object AppointmentMapper {

    fun toMap(a: Appointment): Map<String, Any?> = listOfNotNull(
        a.clientPhone?.let { "clientPhone" to it } // N1: solo citas sin cuenta
    ).toMap() + mapOf(
        "clientUid" to a.clientUid,
        "clientName" to a.clientName,
        "barberId" to a.barberId,
        "barberName" to a.barberName,
        "serviceId" to a.serviceId,
        "serviceName" to a.serviceName,
        "priceCents" to a.price.cents,
        "date" to a.date.toString(),
        "startMinute" to a.startMinute.toLong(),
        "endMinute" to a.endMinute.toLong(),
        "startsAt" to a.startsAt,
        "status" to a.status.name,
        "slotKeys" to a.slots.sorted().map { it.toString() },
        "dayId" to dayId(a.barberId, a.date)
    )

    fun fromMap(id: String, tenantId: String, m: Map<String, Any?>): Appointment? = runCatching {
        Appointment(
            id = id,
            tenantId = tenantId,
            clientUid = m["clientUid"] as? String, // N1: null = sin cuenta
            clientName = m["clientName"] as? String ?: "",
            barberId = m["barberId"] as String,
            barberName = m["barberName"] as? String ?: "",
            serviceId = m["serviceId"] as String,
            serviceName = m["serviceName"] as? String ?: "",
            price = Money((m["priceCents"] as Number).toLong()),
            date = LocalDate.parse(m["date"] as String),
            startMinute = (m["startMinute"] as Number).toInt(),
            endMinute = (m["endMinute"] as Number).toInt(),
            status = AppointmentStatus.valueOf(m["status"] as String),
            startsAt = m["startsAt"] as Instant,
            clientPhone = m["clientPhone"] as? String,
            markedAt = m["updatedAt"] as? Instant // F7-2 (la pone el servidor)
        )
    }.getOrNull()

    /** `{"600": "a1", "605": "a1"}` → {600, 605}. Claves que no son número se ignoran. */
    fun bookedMinutes(day: Map<String, Any?>?): Set<Int> =
        (day?.get("booked") as? Map<*, *>).orEmpty().keys.mapNotNull { (it as? String)?.toIntOrNull() }.toSet()
}
