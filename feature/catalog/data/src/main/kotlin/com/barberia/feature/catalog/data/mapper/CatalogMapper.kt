package com.barberia.feature.catalog.data.mapper

import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.Money
import com.barberia.feature.catalog.domain.model.Service
import com.barberia.feature.catalog.domain.model.TimeOff
import com.barberia.feature.catalog.domain.model.TimeRange
import com.barberia.feature.catalog.domain.model.WeeklySchedule
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Catálogo ↔ mapa de Firestore. Tolerante: una entrada corrupta se descarta en lugar de tumbar la app
 * (el documento lo escribe la app, pero puede venir de una versión vieja).
 */
internal object CatalogMapper {

    fun toMap(c: Catalog): Map<String, Any?> = mapOf(
        "version" to c.version,
        "services" to c.services.map {
            mapOf(
                "id" to it.id,
                "name" to it.name,
                "durationMinutes" to it.durationMinutes.toLong(),
                "priceCents" to it.price.cents,
                "active" to it.active
            )
        },
        "barbers" to c.barbers.map { b ->
            mapOf(
                "id" to b.id,
                "name" to b.name,
                "active" to b.active,
                "schedule" to b.schedule.days.entries.associate { (day, ranges) ->
                    day.name to ranges.map { mapOf("start" to it.startMinute.toLong(), "end" to it.endMinute.toLong()) }
                },
                // N2: fecha ISO; sin start/end = día completo
                "timeOff" to b.timeOff.map { off ->
                    buildMap<String, Any> {
                        put("date", off.date.toString())
                        off.range?.let {
                            put("start", it.startMinute.toLong())
                            put("end", it.endMinute.toLong())
                        }
                    }
                }
            )
        }
    )

    fun fromMap(raw: Map<String, Any?>?): Catalog? {
        raw ?: return null
        return Catalog(
            services = raw.list("services").mapNotNull(::service),
            barbers = raw.list("barbers").mapNotNull(::barber),
            version = (raw["version"] as? Number)?.toLong() ?: 0
        )
    }

    private fun service(any: Any?): Service? {
        val m = any as? Map<*, *> ?: return null
        return Service(
            id = m["id"] as? String ?: return null,
            name = m["name"] as? String ?: return null,
            durationMinutes = (m["durationMinutes"] as? Number)?.toInt() ?: return null,
            price = Money((m["priceCents"] as? Number)?.toLong() ?: return null),
            active = m["active"] as? Boolean ?: true
        )
    }

    private fun barber(any: Any?): Barber? {
        val m = any as? Map<*, *> ?: return null
        return Barber(
            id = m["id"] as? String ?: return null,
            name = m["name"] as? String ?: return null,
            active = m["active"] as? Boolean ?: true,
            schedule = schedule(m["schedule"]),
            timeOff = (m["timeOff"] as? List<*>).orEmpty().mapNotNull(::timeOff)
        )
    }

    private fun timeOff(any: Any?): TimeOff? {
        val m = any as? Map<*, *> ?: return null
        val date = (m["date"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val start = (m["start"] as? Number)?.toInt()
        val end = (m["end"] as? Number)?.toInt()
        return TimeOff(date, if (start != null && end != null) TimeRange(start, end) else null)
    }

    private fun schedule(any: Any?): WeeklySchedule {
        val m = any as? Map<*, *> ?: return WeeklySchedule.EMPTY
        val days = m.entries.mapNotNull { (k, v) ->
            val day = DayOfWeek.entries.find { it.name == k } ?: return@mapNotNull null
            val ranges = (v as? List<*>).orEmpty().mapNotNull { r ->
                val rm = r as? Map<*, *> ?: return@mapNotNull null
                val start = (rm["start"] as? Number)?.toInt() ?: return@mapNotNull null
                val end = (rm["end"] as? Number)?.toInt() ?: return@mapNotNull null
                TimeRange(start, end)
            }
            if (ranges.isEmpty()) null else day to ranges
        }.toMap()
        return WeeklySchedule(days)
    }

    private fun Map<String, Any?>.list(key: String): List<Any?> = (this[key] as? List<*>).orEmpty()
}
