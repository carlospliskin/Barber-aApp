package com.barberia.app.di

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.ShopZones
import com.barberia.feature.booking.domain.repository.BookingRepository
import com.barberia.feature.catalog.domain.usecase.ShopCalendar
import java.time.Clock

/**
 * N2-c: el catálogo no conoce la barbería; su "hoy" sale de la zona que guarda booking (1 lectura del
 * documento de la barbería). Sin poder leerla, la zona de alta: nunca UTC.
 */
fun barbershopCalendar(bookings: BookingRepository, clock: Clock): ShopCalendar = ShopCalendar.of(clock) { tenantId ->
    (bookings.barbershop(tenantId) as? AppResult.Success)?.value?.zone ?: ShopZones.ALLOWED.first()
}
