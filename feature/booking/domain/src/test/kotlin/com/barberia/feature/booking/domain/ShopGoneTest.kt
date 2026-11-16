package com.barberia.feature.booking.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.booking.domain.model.Barbershop
import com.barberia.feature.booking.domain.model.BookingFailure
import com.barberia.feature.booking.domain.testing.FakeBookingRepository
import com.barberia.feature.booking.domain.testing.FakeJoinedStore
import com.barberia.feature.booking.domain.usecase.ObserveJoinedBarbershopUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/** A7-c: la barbería guardada en el teléfono ya no existe (el dueño la borró). */
class ShopGoneTest {
    private val bookings = FakeBookingRepository()
    private val store = FakeJoinedStore()

    @Test fun `se olvida, se avisa una vez y luego queda como sin barberia`() = runTest {
        store.save("cli", "borrada")
        val emitted = ObserveJoinedBarbershopUseCase(bookings, store)("cli").take(2).toList()
        assertEquals(listOf(AppResult.Failure(BookingFailure.ShopGone), AppResult.Success(null)), emitted)
        assertNull(store.tenantId("cli").first())
    }

    @Test fun `una barberia que existe se carga normal`() = runTest {
        val shop = Barbershop("t1", "Gold", ZoneId.of("America/Mexico_City"), "ABC234")
        bookings.barbershops["t1"] = shop
        store.save("cli", "t1")
        assertEquals(AppResult.Success(shop), ObserveJoinedBarbershopUseCase(bookings, store)("cli").first())
    }
}
