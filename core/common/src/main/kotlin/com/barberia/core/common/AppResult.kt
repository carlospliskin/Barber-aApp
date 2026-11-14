package com.barberia.core.common

/**
 * Resultado tipado de una operación: éxito con [T] o fallo con un error de dominio [E].
 * Sustituye a `Either<Failure, T>` del legacy Flutter sin depender de librerías externas.
 */
sealed interface AppResult<out E, out T> {
    data class Success<out T>(val value: T) : AppResult<Nothing, T>
    data class Failure<out E>(val error: E) : AppResult<E, Nothing>
}

inline fun <E, T, R> AppResult<E, T>.fold(onFailure: (E) -> R, onSuccess: (T) -> R): R = when (this) {
    is AppResult.Success -> onSuccess(value)
    is AppResult.Failure -> onFailure(error)
}

inline fun <E, T, R> AppResult<E, T>.map(transform: (T) -> R): AppResult<E, R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

fun <E, T> AppResult<E, T>.getOrNull(): T? = (this as? AppResult.Success)?.value
