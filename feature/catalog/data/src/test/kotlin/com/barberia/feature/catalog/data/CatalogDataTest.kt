package com.barberia.feature.catalog.data

import com.barberia.core.common.AppResult
import com.barberia.core.firebase.GatewayError
import com.barberia.feature.catalog.data.gateway.CatalogGateway
import com.barberia.feature.catalog.data.mapper.CatalogMapper
import com.barberia.feature.catalog.data.repository.FirestoreCatalogRepository
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.model.Money
import com.barberia.feature.catalog.domain.model.Service
import com.barberia.feature.catalog.domain.model.TimeRange
import com.barberia.feature.catalog.domain.model.WeeklySchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class CatalogDataTest {

    private val catalog = Catalog(
        services = listOf(Service("s1", "Corte", 30, Money(15_000), active = true)),
        barbers = listOf(
            Barber(
                "b1", "Luis", active = false,
                schedule = WeeklySchedule(
                    mapOf(DayOfWeek.MONDAY to listOf(TimeRange.of("10:00", "14:00"), TimeRange.of("16:00", "20:00"))),
                ),
            ),
        ),
        version = 7,
    )

    // ── Mapper ────────────────────────────────────────────────────────────────
    @Test fun `ida y vuelta sin perder datos`() =
        assertEquals(catalog, CatalogMapper.fromMap(CatalogMapper.toMap(catalog)))

    @Test fun `Firestore devuelve numeros como Long y puede faltar algun campo`() {
        val raw = mapOf(
            "version" to 2L,
            "services" to listOf(mapOf("id" to "s1", "name" to "Corte", "durationMinutes" to 30L, "priceCents" to 15000L)),
        )
        val c = CatalogMapper.fromMap(raw)!!
        assertEquals(Service("s1", "Corte", 30, Money(15_000), active = true), c.services.single())
        assertEquals(emptyList<Barber>(), c.barbers)
    }

    @Test fun `entradas corruptas se ignoran en vez de tumbar la app`() {
        val raw = mapOf(
            "version" to 1L,
            "services" to listOf(mapOf("name" to "sin id"), "basura"),
            "barbers" to listOf(mapOf("id" to "b1", "name" to "Luis", "schedule" to mapOf("FUNDAY" to listOf(1)))),
        )
        val c = CatalogMapper.fromMap(raw)!!
        assertEquals(emptyList<Service>(), c.services)
        assertEquals(WeeklySchedule.EMPTY, c.barbers.single().schedule)
    }

    @Test fun `sin documento no hay catalogo`() = assertEquals(null, CatalogMapper.fromMap(null))

    // ── Repositorio ───────────────────────────────────────────────────────────
    private class FakeGateway(var doc: Map<String, Any?>? = null) : CatalogGateway {
        val live = MutableStateFlow(doc)
        var error: GatewayError? = null
        override fun observe(tenantId: String) = live
        override suspend fun transact(tenantId: String, update: (Map<String, Any?>?) -> Map<String, Any?>?) {
            error?.let { throw it }
            update(doc)?.let { doc = it; live.value = it }
        }
    }

    @Test fun `C10 barberia sin documento se ve como catalogo vacio`() = runTest {
        assertEquals(Catalog.empty(), FirestoreCatalogRepository(FakeGateway()).observe("t").first())
    }

    @Test fun `editar guarda con version + 1`() = runTest {
        val gw = FakeGateway(CatalogMapper.toMap(catalog))
        val repo = FirestoreCatalogRepository(gw)
        val r = repo.edit("t") { AppResult.Success(it.copy(services = emptyList())) }
        assertEquals(8L, (r as AppResult.Success).value.version)
        assertEquals(8L, gw.doc!!["version"])
    }

    @Test fun `si la regla falla no se escribe nada`() = runTest {
        val gw = FakeGateway(CatalogMapper.toMap(catalog))
        val repo = FirestoreCatalogRepository(gw)
        repo.edit("t") { AppResult.Failure(CatalogFailure.NotFound) }
        assertEquals(7L, gw.doc!!["version"])
    }

    @Test fun `errores de Firebase se traducen`() = runTest {
        val gw = FakeGateway()
        val repo = FirestoreCatalogRepository(gw)
        gw.error = GatewayError.Network()
        assertEquals(AppResult.Failure(CatalogFailure.NoConnection), repo.edit("t") { AppResult.Success(it) })
        gw.error = GatewayError.PermissionDenied()
        assertEquals(AppResult.Failure(CatalogFailure.NotOwner), repo.edit("t") { AppResult.Success(it) })
    }
}
