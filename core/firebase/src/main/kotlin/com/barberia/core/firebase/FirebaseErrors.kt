package com.barberia.core.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException

/**
 * Errores de Firebase ya clasificados. Los gateways los lanzan y cada repositorio los traduce a su
 * propio tipo de fallo de dominio. Así ningún repositorio conoce las excepciones del SDK.
 */
sealed class GatewayError(cause: Throwable? = null) : Exception(cause) {
    class InvalidCredentials(cause: Throwable? = null) : GatewayError(cause)
    class EmailInUse(cause: Throwable? = null) : GatewayError(cause)
    class WeakPassword(cause: Throwable? = null) : GatewayError(cause)
    class Network(cause: Throwable? = null) : GatewayError(cause)
    class TooManyRequests(cause: Throwable? = null) : GatewayError(cause)
    class PermissionDenied(cause: Throwable? = null) : GatewayError(cause)
    class Other(cause: Throwable? = null) : GatewayError(cause)
}

/** Ejecuta una llamada al SDK y traduce sus excepciones. El orden importa: WeakPassword hereda de InvalidCredentials. */
suspend fun <T> firebaseCall(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: GatewayError) {
    throw e
} catch (e: FirebaseAuthWeakPasswordException) {
    throw GatewayError.WeakPassword(e)
} catch (e: FirebaseAuthInvalidCredentialsException) {
    throw GatewayError.InvalidCredentials(e)
} catch (e: FirebaseAuthInvalidUserException) {
    throw GatewayError.InvalidCredentials(e)
} catch (e: FirebaseAuthUserCollisionException) {
    throw GatewayError.EmailInUse(e)
} catch (e: FirebaseNetworkException) {
    throw GatewayError.Network(e)
} catch (e: FirebaseTooManyRequestsException) {
    throw GatewayError.TooManyRequests(e)
} catch (e: FirebaseFirestoreException) {
    throw e.toGatewayError()
} catch (e: Exception) {
    throw GatewayError.Other(e)
}

fun FirebaseFirestoreException.toGatewayError(): GatewayError = when (code) {
    FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> GatewayError.Network(this)

    FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.UNAUTHENTICATED ->
        GatewayError.PermissionDenied(this)

    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> GatewayError.TooManyRequests(this)

    else -> GatewayError.Other(this)
}
