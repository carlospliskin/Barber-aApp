package com.barberia.core.network

import kotlinx.serialization.json.JsonObject

/** Contrato mínimo para hablar con el backend GraphQL (DIP: los datasources dependen de esto, no de OkHttp). */
interface GraphQLClient {
    /** Ejecuta una query o mutation y devuelve el nodo `data`. Lanza [NetworkException] si falla. */
    suspend fun execute(
        document: String,
        variables: Map<String, Any?> = emptyMap(),
        token: String? = null,
    ): JsonObject
}

/** Errores de transporte; cada feature los traduce a su propio tipo de fallo de dominio. */
sealed class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoConnection(cause: Throwable) : NetworkException("Sin conexión", cause)
    class Http(val code: Int) : NetworkException("HTTP $code")
    class GraphQL(val errors: List<String>) : NetworkException(errors.joinToString())
    class Malformed(cause: Throwable) : NetworkException("Respuesta inválida", cause)
}
