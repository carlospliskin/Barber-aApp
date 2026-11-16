package com.barberia.core.mvi

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MviViewModelTest {

    data class S(val log: List<String> = emptyList()) : UiState
    sealed interface I : UiIntent {
        data class Slow(val id: String, val gate: CompletableDeferred<Unit>) : I
        data class Fast(val id: String) : I
        data object Ping : I
        data object Boom : I
    }
    data object Pong : UiEffect

    class TestViewModel : MviViewModel<S, I, Pong>(S()) {
        override suspend fun handleIntent(intent: I) {
            when (intent) {
                is I.Slow -> {
                    intent.gate.await()
                    setState { copy(log = log + intent.id) }
                }
                is I.Fast -> setState { copy(log = log + intent.id) }
                I.Ping -> sendEffect(Pong)
                I.Boom -> error("falla inesperada")
            }
        }

        val unexpected = mutableListOf<Throwable>()
        override fun onUnexpectedError(error: Throwable) {
            unexpected += error
        }

        fun listenAndFail() = launchInScope { error("se cayó el listener") }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `los intents se procesan en el orden en que llegan`() = runTest {
        val vm = TestViewModel()
        val gate = CompletableDeferred<Unit>()

        vm.onIntent(I.Slow("primero", gate))
        vm.onIntent(I.Fast("segundo"))
        assertEquals(emptyList<String>(), vm.state.value.log) // el segundo espera al primero

        gate.complete(Unit)
        assertEquals(listOf("primero", "segundo"), vm.state.value.log)
    }

    @Test fun `los efectos se entregan una sola vez`() = runTest {
        val vm = TestViewModel()
        vm.effects.test {
            vm.onIntent(I.Ping)
            assertEquals(Pong, awaitItem())
            expectNoEvents()
        }
    }

    // ── Errores inesperados: no tumban la app ni la pantalla ─────────────────
    @Test fun `una falla en un intent no cierra la app y los siguientes intents siguen funcionando`() = runTest {
        val vm = TestViewModel()
        vm.onIntent(I.Boom)
        vm.onIntent(I.Fast("despues"))
        assertEquals(listOf("despues"), vm.state.value.log)
        assertEquals("falla inesperada", vm.unexpected.single().message)
    }

    @Test fun `una falla en un trabajo largo llega a onUnexpectedError`() = runTest {
        val vm = TestViewModel()
        vm.listenAndFail()
        assertEquals("se cayó el listener", vm.unexpected.single().message)
    }
}
