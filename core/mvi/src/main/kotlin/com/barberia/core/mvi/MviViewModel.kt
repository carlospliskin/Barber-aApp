package com.barberia.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado inmutable que la pantalla dibuja. */
interface UiState

/** Acción del usuario o del sistema que entra al ViewModel. */
interface UiIntent

/** Evento de una sola vez (navegar, mostrar un mensaje); no forma parte del estado. */
interface UiEffect

/**
 * Base MVI con flujo unidireccional:
 * `Intent → handleIntent (casos de uso) → setState (reducer puro) → State`, y efectos aparte.
 *
 * - Los intents se procesan **uno a la vez, en orden de llegada** (cola). Así un doble toque en
 *   "Enviar" no corre dos veces en paralelo y el estado nunca se pisa.
 * - Para trabajo que dura toda la vida del ViewModel (escuchar un Flow), usar [launchInScope]
 *   para no bloquear la cola.
 * - La vista solo llama [onIntent] y observa [state] / [effects] (con `CollectEffects`).
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    private val intents = Channel<I>(Channel.UNLIMITED)

    protected val currentState: S get() = _state.value

    init {
        viewModelScope.launch {
            // Una falla en un intent no debe cerrar la app ni dejar la pantalla sin responder.
            for (intent in intents) guarded { handleIntent(intent) }
        }
    }

    fun onIntent(intent: I) {
        intents.trySend(intent)
    }

    protected abstract suspend fun handleIntent(intent: I)

    /** Aplica una función pura `S -> S`; es el único lugar donde cambia el estado. */
    protected fun setState(reducer: S.() -> S) = _state.update(reducer)

    protected suspend fun sendEffect(effect: E) = _effects.send(effect)

    /** Trabajo largo (p. ej. observar la sesión) fuera de la cola de intents. */
    protected fun launchInScope(block: suspend () -> Unit) {
        viewModelScope.launch { guarded(block) }
    }

    /**
     * Error que nadie manejó (un `catch` faltante en un Flow, un bug). Sin esto, `viewModelScope`
     * cerraría la app. Por defecto se reporta a [MviErrors]; la pantalla puede mostrar algo propio.
     */
    protected open fun onUnexpectedError(error: Throwable) = MviErrors.report(error)

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onUnexpectedError(e)
        }
    }
}

/** Donde terminan los errores inesperados de los ViewModel. `app` lo conecta a Crashlytics (no fatal). */
object MviErrors {
    @Volatile var reporter: (Throwable) -> Unit = {}
    fun report(error: Throwable) = reporter(error)
}
