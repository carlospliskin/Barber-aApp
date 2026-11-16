package com.barberia.feature.auth.data.firebase

import com.barberia.feature.auth.data.gateway.AccountInfo
import com.barberia.feature.auth.data.gateway.AuthGateway
import com.barberia.feature.auth.data.gateway.GatewayError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

internal class FirebaseAuthGateway(private val auth: FirebaseAuth) : AuthGateway {

    override val uidChanges: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override fun currentAccount(): AccountInfo? = auth.currentUser?.let {
        AccountInfo(it.uid, it.email, it.displayName, it.isEmailVerified)
    }

    override suspend fun signIn(email: String, password: String) {
        firebaseCall { auth.signInWithEmailAndPassword(email, password).await() }
    }

    override suspend fun createUser(email: String, password: String): String = firebaseCall {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user
        user?.uid ?: throw GatewayError.Other(IllegalStateException("Firebase no devolvió el usuario"))
    }

    override suspend fun sendEmailVerification() {
        firebaseCall { auth.currentUser?.sendEmailVerification()?.await() }
    }

    override suspend fun sendPasswordReset(email: String) {
        firebaseCall { auth.sendPasswordResetEmail(email).await() }
    }

    override suspend fun deleteCurrentUser() {
        firebaseCall { auth.currentUser?.delete()?.await() }
    }

    override fun signOut() = auth.signOut()
}

/** Traduce las excepciones de Firebase a [GatewayError]. El orden importa: WeakPassword hereda de InvalidCredentials. */
internal suspend fun <T> firebaseCall(block: suspend () -> T): T = try {
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
    throw if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) GatewayError.Network(e) else GatewayError.Other(e)
} catch (e: Exception) {
    throw GatewayError.Other(e)
}
