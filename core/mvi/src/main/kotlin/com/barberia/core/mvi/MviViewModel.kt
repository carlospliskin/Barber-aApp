package com.barberia.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/** Evento de una sola vez (navegar, mostrar un toast); no forma parte del estado. */
interface UiEffect

/**
 * Base MVI con flujo unidireccional:
 * `Intent → handleIntent (casos de uso) → setState (reducer) → State`, y efectos aparte.
 *
 * - La vista solo llama [onIntent] y observa [state] / [effects].
 * - Las subclases solo conocen casos de uso (DIP); no saben de red ni almacenamiento.
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    protected val currentState: S get() = _state.value

    fun onIntent(intent: I) {
        viewModelScope.launch { handleIntent(intent) }
    }

    protected abstract suspend fun handleIntent(intent: I)

    /** Aplica una función pura `S -> S`; es el único lugar donde cambia el estado. */
    protected fun setState(reducer: S.() -> S) = _state.update(reducer)

    protected suspend fun sendEffect(effect: E) = _effects.send(effect)
}
