package com.barberia.feature.booking.domain.usecase

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.availability.AvailabilityCalculator
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingPolicy
import com.barberia.feature.booking.domain.model.BookingRequest
import com.barberia.feature.booking.domain.model.BookingResult
import com.barberia.feature.booking.domain.model.slotKeys
import com.barberia.feature.booking.domain.repository.BookingRepository
import com.barberia.feature.booking.domain.repository.JoinedBarbershopStore
import com.barberia.feature.catalog.domain.editor.IdGenerator
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.CatalogException
import com.barberia.feature.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Random

private fun Clock.nowIn(zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(instant(), zone)

private suspend fun CatalogRepository.current(tenantId: String): BookingResult<Catalog> = try {
    AppResult.Success(observe(tenantId).first())
} catch (e: CatalogException) {
    AppResult.Failure(BookingFailure.NoConnection)
}

class GetAvailableSlotsUseCase(
    private val catalog: CatalogRepository,
    private val bookings: BookingRepository,
    private val policy: BookingPolicy,
    private val clock: Clock,
) {
    private val calculator = AvailabilityCalculator(policy)

    suspend operator fun invoke(
        tenantId: String,
        zone: ZoneId,
        barberId: String,
        serviceId: String,
        date: LocalDate,
    ): BookingResult<List<Int>> {
        val c = when (val r = catalog.current(tenantId)) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> return r
        }
        val barber = c.barbers.find { it.id == barberId } ?: return AppResult.Failure(BookingFailure.BarberUnavailable)
        val service = c.services.find { it.id == serviceId } ?: return AppResult.Failure(BookingFailure.ServiceUnavailable)
        return when (val booked = bookings.dayBookings(tenantId, barberId, date)) {
            is AppResult.Success -> AppResult.Success(calculator.slots(barber, service, date, booked.value, clock.nowIn(zone)))
            is AppResult.Failure -> booked
        }
    }
}

class BookAppointmentUseCase(
    private val catalog: CatalogRepository,
    private val bookings: BookingRepository,
    private val policy: BookingPolicy,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val calculator = AvailabilityCalculator(policy)

    suspend operator fun invoke(req: BookingRequest): BookingResult<Appointment> {
        // B9: se valida contra el catálogo de ESTE momento, no el que vio la pantalla.
        val c = when (val r = catalog.current(req.tenantId)) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> return r
        }
        val service = c.bookableServices.find { it.id == req.serviceId }
            ?: return AppResult.Failure(BookingFailure.ServiceUnavailable)
        val barber = c.bookableBarbers.find { it.id == req.barberId }
            ?: return AppResult.Failure(BookingFailure.BarberUnavailable)
        val now = clock.nowIn(req.zone)

        // B1, B2, B8: el inicio debe ser uno de los ofrecidos (sin contar lo ya ocupado).
        if (req.startMinute !in calculator.slots(barber, service, req.date, emptySet(), now)) {
            return AppResult.Failure(BookingFailure.SlotUnavailable)
        }
        // B5
        when (val active = bookings.countActive(req.tenantId, req.clientUid, now.toLocalDate())) {
            is AppResult.Failure -> return active
            is AppResult.Success -> if (active.value >= policy.maxActivePerClient) {
                return AppResult.Failure(BookingFailure.TooManyActive)
            }
        }
        // B3 (aviso temprano; la garantía real es la escritura atómica del repositorio + reglas)
        val wanted = slotKeys(req.startMinute, service.durationMinutes)
        when (val day = bookings.dayBookings(req.tenantId, barber.id, req.date)) {
            is AppResult.Failure -> return day
            is AppResult.Success -> if (wanted.any { it in day.value }) return AppResult.Failure(BookingFailure.SlotTaken)
        }
        val appointment = Appointment(
            id = ids.next(),
            tenantId = req.tenantId,
            clientUid = req.clientUid,
            clientName = req.clientName,
            barberId = barber.id,
            barberName = barber.name,
            serviceId = service.id,
            serviceName = service.name,
            price = service.price,
            date = req.date,
            startMinute = req.startMinute,
            endMinute = req.startMinute + service.durationMinutes,
            status = AppointmentStatus.BOOKED,
            startsAt = req.date.atStartOfDay(req.zone).plusMinutes(req.startMinute.toLong()).toInstant(),
        )
        return when (val saved = bookings.book(appointment)) {
            is AppResult.Success -> AppResult.Success(appointment)
            is AppResult.Failure -> saved
        }
    }
}

class CancelAppointmentUseCase(
    private val bookings: BookingRepository,
    private val policy: BookingPolicy,
    private val clock: Clock,
) {
    suspend operator fun invoke(appointment: Appointment, zone: ZoneId, byUid: String): BookingResult<Unit> {
        if (appointment.clientUid != byUid) return AppResult.Failure(BookingFailure.NotYours)
        if (appointment.status != AppointmentStatus.BOOKED) return AppResult.Failure(BookingFailure.AlreadyClosed)
        val start = appointment.date.atStartOfDay().plusMinutes(appointment.startMinute.toLong())
        if (clock.nowIn(zone).isAfter(start.minusHours(policy.cancelHoursBefore))) {
            return AppResult.Failure(BookingFailure.CancelTooLate) // B7
        }
        return bookings.cancel(appointment)
    }
}

class ObserveMyAppointmentsUseCase(private val bookings: BookingRepository, private val clock: Clock) {
    /** R4: solo de hoy (en la zona de la barbería) en adelante. */
    operator fun invoke(tenantId: String, clientUid: String, zone: ZoneId): Flow<List<Appointment>> =
        bookings.observeUpcoming(tenantId, clientUid, clock.nowIn(zone).toLocalDate())
}

class JoinBarbershopUseCase(
    private val bookings: BookingRepository,
    private val store: JoinedBarbershopStore,
) {
    /** B0/R8: código de 6 caracteres → barbería; se guarda en el teléfono. */
    suspend operator fun invoke(rawCode: String, uid: String): BookingResult<Barbershop> {
        val code = ShareCodes.normalize(rawCode) ?: return AppResult.Failure(BookingFailure.InvalidCode)
        val tenantId = when (val r = bookings.resolveShareCode(code)) {
            is AppResult.Success -> r.value ?: return AppResult.Failure(BookingFailure.InvalidCode)
            is AppResult.Failure -> return r
        }
        val shop = when (val r = bookings.barbershop(tenantId)) {
            is AppResult.Success -> r.value ?: return AppResult.Failure(BookingFailure.InvalidCode)
            is AppResult.Failure -> return r
        }
        store.save(uid, tenantId)
        return AppResult.Success(shop)
    }
}

class ObserveJoinedBarbershopUseCase(
    private val bookings: BookingRepository,
    private val store: JoinedBarbershopStore,
) {
    /** Barbería guardada (Success(null) si todavía no elige una). */
    operator fun invoke(uid: String): Flow<BookingResult<Barbershop?>> = store.tenantId(uid).map { id ->
        if (id == null) AppResult.Success(null) else bookings.barbershop(id)
    }
}

class LeaveBarbershopUseCase(private val bookings: BookingRepository, private val store: JoinedBarbershopStore) {
    /** V1: no se deja una barbería con citas activas (dejarían de verse y el cliente las olvidaría). */
    suspend operator fun invoke(uid: String, tenantId: String, today: LocalDate): BookingResult<Unit> {
        when (val active = bookings.countActive(tenantId, uid, today)) {
            is AppResult.Failure -> return active
            is AppResult.Success -> if (active.value > 0) return AppResult.Failure(BookingFailure.HasActiveAppointments)
        }
        store.save(uid, null)
        return AppResult.Success(Unit)
    }
}

class EnsureShareCodeUseCase(private val bookings: BookingRepository, private val random: Random) {
    /** R7: devuelve el código de la barbería; si no tiene, crea uno libre (reintenta si choca). */
    suspend operator fun invoke(tenantId: String): BookingResult<String> {
        val shop = when (val r = bookings.barbershop(tenantId)) {
            is AppResult.Success -> r.value ?: return AppResult.Failure(BookingFailure.NotYours)
            is AppResult.Failure -> return r
        }
        shop.shareCode?.let { return AppResult.Success(it) }
        repeat(MAX_ATTEMPTS) {
            val code = ShareCodes.generate(random)
            when (val claimed = bookings.claimShareCode(tenantId, code)) {
                is AppResult.Failure -> return claimed
                is AppResult.Success -> if (claimed.value) return AppResult.Success(code)
            }
        }
        return AppResult.Failure(BookingFailure.Unknown("No se encontró un código libre"))
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

/** B0: códigos cortos para compartir por WhatsApp. Sin 0/O, 1/I/L para no confundirse al dictarlos. */
object ShareCodes {
    const val LENGTH = 6
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun generate(random: Random = Random()): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    fun normalize(raw: String): String? {
        val clean = raw.uppercase().filter { it.isLetterOrDigit() }
        return clean.takeIf { it.length == LENGTH && it.all { ch -> ch in ALPHABET } }
    }
}
