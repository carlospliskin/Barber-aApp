package com.barberia.feature.booking.data.mapper

import com.barberia.feature.booking.domain.model.ClientRecord
import java.time.Instant

/** Ficha del cliente ↔ mapa. Los campos coinciden con `validShape()` de firestore.rules. */
internal object ClientMapper {
    fun toMap(c: ClientRecord, lastAppointmentId: String): Map<String, Any?> = mapOf(
        "name" to c.name,
        "completed" to c.completed.toLong(),
        "noShows" to c.noShows.toLong(),
        "rewardsUsed" to c.rewardsUsed.toLong(),
        "penaltyUntil" to c.penaltyUntil,
        "lastAppointmentId" to lastAppointmentId
    )

    fun fromMap(uid: String, m: Map<String, Any?>?): ClientRecord? {
        m ?: return null
        return ClientRecord(
            uid = uid,
            name = m["name"] as? String ?: "",
            completed = (m["completed"] as? Number)?.toInt() ?: 0,
            noShows = (m["noShows"] as? Number)?.toInt() ?: 0,
            rewardsUsed = (m["rewardsUsed"] as? Number)?.toInt() ?: 0,
            penaltyUntil = m["penaltyUntil"] as? Instant
        )
    }
}
