package com.barberia.feature.catalog.presentation

import com.barberia.feature.auth.domain.model.SessionState
import com.barberia.feature.auth.domain.model.StaffRole
import com.barberia.feature.auth.domain.testing.FakeSessionRepository
import com.barberia.feature.auth.domain.testing.user
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.catalog.domain.editor.CatalogEditor
import com.barberia.feature.catalog.domain.model.Barber
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.CatalogError
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.model.Money
import com.barberia.feature.catalog.domain.model.Service
import com.barberia.feature.catalog.domain.model.TimeRange
import com.barberia.feature.catalog.domain.model.WeeklySchedule
import com.barberia.feature.catalog.domain.testing.FakeCatalogRepository
import com.barberia.feature.catalog.domain.usecase.AddBarberUseCase
import com.barberia.feature.catalog.domain.usecase.AddServiceUseCase
import com.barberia.feature.catalog.domain.usecase.ObserveCatalogUseCase
import com.barberia.feature.catalog.domain.usecase.RenameBarberUseCase
import com.barberia.feature.catalog.domain.usecase.SetBarberActiveUseCase
import com.barberia.feature.catalog.domain.usecase.SetBarberScheduleUseCase
import com.barberia.feature.catalog.domain.usecase.SetServiceActiveUseCase
import com.barberia.feature.catalog.domain.usecase.UpdateServiceUseCase
import com.barberia.feature.catalog.presentation.CatalogDialog.BarberForm
import com.barberia.feature.catalog.presentation.CatalogDialog.Schedule
import com.barberia.feature.catalog.presentation.CatalogDialog.ServiceForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private var ids = 0
    private val editor = CatalogEditor { "id-${++ids}" }
    private val catalogRepo = FakeCatalogRepository()

    private fun vm(role: StaffRole? = StaffRole.OWNER): CatalogViewModel {
        val session = FakeSessionRepository(SessionState.SignedIn(user(role)))
        return CatalogViewModel(
            ObserveCurrentUserUseCase(session),
            ObserveCatalogUseCase(catalogRepo),
            AddServiceUseCase(catalogRepo, editor),
            UpdateServiceUseCase(catalogRepo, editor),
            SetServiceActiveUseCase(catalogRepo, editor),
            AddBarberUseCase(catalogRepo, editor),
            RenameBarberUseCase(catalogRepo, editor),
            SetBarberActiveUseCase(catalogRepo, editor),
            SetBarberScheduleUseCase(catalogRepo, editor),
        )
    }

    private fun CatalogViewModel.newService(name: String, minutes: String, price: String) {
        onIntent(CatalogIntent.OpenNewService)
        onIntent(CatalogIntent.ServiceNameChanged(name))
        onIntent(CatalogIntent.ServiceDurationChanged(minutes))
        onIntent(CatalogIntent.ServicePriceChanged(price))
        onIntent(CatalogIntent.SaveDialog)
    }

    @Test fun `muestra el catalogo en vivo de la barberia del duenio`() {
        val vm = vm()
        assertTrue(vm.state.value.canEdit)
        catalogRepo.stored.value = Catalog(listOf(Service("s", "Corte", 30, Money(100))), emptyList(), 1)
        assertEquals("Corte", vm.state.value.catalog.services.single().name)
    }

    @Test fun `C11 quien no es duenio lo ve en solo lectura y no guarda`() {
        val vm = vm(StaffRole.BARBER)
        assertFalse(vm.state.value.canEdit)
        vm.newService("Corte", "30", "150")
        assertEquals(0, catalogRepo.edits)
    }

    @Test fun `C12 guardar servicio convierte el precio a centavos y cierra el dialogo`() {
        val vm = vm()
        vm.newService("Corte", "30", "150.5")
        assertEquals(Money(15_050), catalogRepo.stored.value.services.single().price)
        assertNull(vm.state.value.dialog)
    }

    @Test fun `C12 precio o duracion que no son numero marcan el campo sin llamar al repositorio`() {
        val vm = vm()
        vm.newService("Corte", "media hora", "abc")
        val form = vm.state.value.dialog as ServiceForm
        assertEquals(setOf(CatalogError.DURATION_INVALID, CatalogError.PRICE_INVALID), form.errors)
        assertEquals(0, catalogRepo.edits)
    }

    @Test fun `C12 conversion de precio`() {
        assertEquals(Money(15_000), parsePrice("150"))
        assertEquals(Money(15_050), parsePrice(" 150.50 "))
        assertEquals(Money(15_050), parsePrice("150,5"))
        assertEquals(Money(1_999), parsePrice("$19.99"))
        assertNull(parsePrice("1.999"))
        assertNull(parsePrice("-5"))
        assertNull(parsePrice(""))
    }

    @Test fun `un error del dominio se muestra en el dialogo y no lo cierra`() {
        val vm = vm()
        vm.newService("Corte", "30", "150")
        vm.newService("corte", "30", "150")
        assertEquals(setOf(CatalogError.NAME_DUPLICATED), (vm.state.value.dialog as ServiceForm).errors)
    }

    @Test fun `sin conexion el dialogo sigue abierto y avisa`() {
        val vm = vm()
        catalogRepo.failure = CatalogFailure.NoConnection
        vm.newService("Corte", "30", "150")
        assertTrue(vm.state.value.dialog is ServiceForm)
        assertEquals(CatalogFailure.NoConnection, vm.state.value.failure)
    }

    @Test fun `editar servicio precarga el formulario`() {
        catalogRepo.stored.value = Catalog(listOf(Service("s", "Corte", 30, Money(15_050))), emptyList(), 1)
        val vm = vm()
        vm.onIntent(CatalogIntent.OpenEditService("s"))
        assertEquals(ServiceForm(editingId = "s", name = "Corte", duration = "30", price = "150.50"), vm.state.value.dialog)
    }

    @Test fun `activar y desactivar servicio`() {
        val vm = vm()
        vm.newService("Corte", "30", "150")
        vm.onIntent(CatalogIntent.SetServiceActive("id-1", false))
        assertFalse(catalogRepo.stored.value.services.single().active)
    }

    @Test fun `agregar barbero`() {
        val vm = vm()
        vm.onIntent(CatalogIntent.OpenNewBarber)
        vm.onIntent(CatalogIntent.BarberNameChanged("Luis"))
        vm.onIntent(CatalogIntent.SaveDialog)
        assertEquals("Luis", catalogRepo.stored.value.barbers.single().name)
        assertNull(vm.state.value.dialog)
    }

    @Test fun `horario precarga los dias que trabaja`() {
        val monday = TimeRange.of("10:00", "19:00")
        catalogRepo.stored.value = Catalog(
            emptyList(),
            listOf(Barber("b", "Luis", schedule = WeeklySchedule(mapOf(DayOfWeek.MONDAY to listOf(monday))))),
            1,
        )
        val vm = vm()
        vm.onIntent(CatalogIntent.OpenSchedule("b"))
        val s = vm.state.value.dialog as Schedule
        assertEquals(DayDraft(enabled = true, start = "10:00", end = "19:00"), s.days.getValue(DayOfWeek.MONDAY))
        assertFalse(s.days.getValue(DayOfWeek.SUNDAY).enabled)
    }

    @Test fun `guardar horario solo con los dias activos`() {
        val vm = vm()
        vm.onIntent(CatalogIntent.OpenNewBarber)
        vm.onIntent(CatalogIntent.BarberNameChanged("Luis"))
        vm.onIntent(CatalogIntent.SaveDialog)
        vm.onIntent(CatalogIntent.OpenSchedule("id-1"))
        vm.onIntent(CatalogIntent.DayToggled(DayOfWeek.TUESDAY, true))
        vm.onIntent(CatalogIntent.DayStartChanged(DayOfWeek.TUESDAY, "09:00"))
        vm.onIntent(CatalogIntent.DayEndChanged(DayOfWeek.TUESDAY, "17:30"))
        vm.onIntent(CatalogIntent.SaveDialog)
        assertEquals(
            mapOf(DayOfWeek.TUESDAY to listOf(TimeRange.of("09:00", "17:30"))),
            catalogRepo.stored.value.barbers.single().schedule.days,
        )
        assertNull(vm.state.value.dialog)
    }

    @Test fun `hora mal escrita marca error de horario`() {
        val vm = vm()
        vm.onIntent(CatalogIntent.OpenNewBarber)
        vm.onIntent(CatalogIntent.BarberNameChanged("Luis"))
        vm.onIntent(CatalogIntent.SaveDialog)
        vm.onIntent(CatalogIntent.OpenSchedule("id-1"))
        vm.onIntent(CatalogIntent.DayToggled(DayOfWeek.TUESDAY, true))
        vm.onIntent(CatalogIntent.DayStartChanged(DayOfWeek.TUESDAY, "9am"))
        vm.onIntent(CatalogIntent.SaveDialog)
        assertEquals(setOf(CatalogError.SCHEDULE_RANGE_INVALID), (vm.state.value.dialog as Schedule).errors)
    }
}
