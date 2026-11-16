package com.barberia.feature.booking.domain.usecase

import com.barberia.core.common.AppResult
import com.barberia.core.common.ShortCodes
import com.barberia.feature.booking.domain.availability.AvailabilityCalculator
import com.barberia.feature.booking.domain.model.Appointment
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.AppointmentStatus
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.model.BookingPolicy
import com.barberia.feature.booking.domain.model.BookingRequest
import com.barberia.feature.booking.domain.model.BookingResult
import com.barberia.feature.booking.domain.model.WalkInRequest
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
    private val staffCalculator = AvailabilityCalculator(policy.forStaff())

    suspend operator fun invoke(
        tenantId: String,
        zone: ZoneId,
        barberId: String,
        serviceId: String,
        date: LocalDate,
        /** N1: el personal ve también el turno actual. */
        forStaff: Boolean = false,
    ): BookingResult<List<Int>> {
        val c = when (val r = catalog.current(tenantId)) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> return r
        }
        val barber = c.barbers.find { it.id == barberId } ?: return AppResult.Failure(BookingFailure.BarberUnavailable)
        val service = c.services.find { it.id == serviceId } ?: return AppResult.Failure(BookingFailure.ServiceUnavailable)
        return when (val booked = bookings.dayBookings(tenantId, barberId, date)) {
            is AppResult.Success -> AppResult.Success(
                (if (forStaff) staffCalculator else calculator).slots(barber, service, date, booked.value, clock.nowIn(zone)),
            )
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
        // P2 (B4): penalizado por no-shows
        when (val client = bookings.client(req.tenantId, req.clientUid)) {
            is AppResult.Failure -> return client
            is AppResult.Success -> client.value?.penaltyUntil?.let { until ->
                if (until.isAfter(clock.instant())) return AppResult.Failure(BookingFailure.Penalized(until))
            }
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

/**
 * N1: el personal agenda a un cliente sin cuenta (por teléfono o en el local). Sin ficha, sin penalización
 * ni límite de citas activas (no hay a quién contarlas). Sí respeta horario, días libres y bloques ocupados.
 */
class BookWalkInUseCase(
    private val catalog: CatalogRepository,
    private val bookings: BookingRepository,
    policy: BookingPolicy,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val calculator = AvailabilityCalculator(policy.forStaff())

    suspend operator fun invoke(req: WalkInRequest): BookingResult<Appointment> {
        val name = req.clientName.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty() || name.length > MAX_NAME) return AppResult.Failure(BookingFailure.InvalidClient)
        val phone = req.clientPhone?.filterNot { it == ' ' || it == '-' }?.takeIf { it.isNotEmpty() }
        if (phone != null && (phone.length > MAX_PHONE || !phone.removePrefix("+").all { it.isDigit() })) {
            return AppResult.Failure(BookingFailure.InvalidClient)
        }
        val c = when (val r = catalog.current(req.tenantId)) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> return r
        }
        val service = c.bookableServices.find { it.id == req.serviceId }
            ?: return AppResult.Failure(BookingFailure.ServiceUnavailable)
        val barber = c.bookableBarbers.find { it.id == req.barberId }
            ?: return AppResult.Failure(BookingFailure.BarberUnavailable)
        if (req.startMinute !in calculator.slots(barber, service, req.date, emptySet(), clock.nowIn(req.zone))) {
            return AppResult.Failure(BookingFailure.SlotUnavailable)
        }
        val wanted = slotKeys(req.startMinute, service.durationMinutes)
        when (val day = bookings.dayBookings(req.tenantId, barber.id, req.date)) {
            is AppResult.Failure -> return day
            is AppResult.Success -> if (wanted.any { it in day.value }) return AppResult.Failure(BookingFailure.SlotTaken)
        }
        val appointment = Appointment(
            id = ids.next(),
            tenantId = req.tenantId,
            clientUid = null,
            clientName = name,
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
            clientPhone = phone,
        )
        return when (val saved = bookings.book(appointment)) {
            is AppResult.Success -> AppResult.Success(appointment)
            is AppResult.Failure -> saved
        }
    }

    private companion object {
        const val MAX_NAME = 80
        const val MAX_PHONE = 20
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

/** B0: códigos para compartir la barbería (mismo formato que las invitaciones). */
object ShareCodes {
    const val LENGTH = ShortCodes.LENGTH
    fun generate(random: Random = Random()): String = ShortCodes.generate(random)
    fun normalize(raw: String): String? = ShortCodes.normalize(raw)
}
