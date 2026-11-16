package com.barberia.feature.auth.data.firebase

import com.barberia.core.firebase.CachePurge
import com.barberia.core.firebase.GatewayError
import com.barberia.core.firebase.firebaseCall
import com.barberia.feature.auth.data.gateway.AccountInfo
import com.barberia.feature.auth.data.gateway.AuthGateway
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

internal class FirebaseAuthGateway(private val auth: FirebaseAuth, private val purge: CachePurge) : AuthGateway {

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

    override suspend fun reload(): Boolean = firebaseCall {
        val user = auth.currentUser ?: return@firebaseCall false
        user.reload().await()
        // A15: sin forzar un token nuevo, las reglas ven email_verified=false hasta 1 hora.
        user.getIdToken(true).await()
        auth.currentUser?.isEmailVerified == true
    }

    override suspend fun sendPasswordReset(email: String) {
        firebaseCall { auth.sendPasswordResetEmail(email).await() }
    }

    override suspend fun deleteCurrentUser() {
        firebaseCall { auth.currentUser?.delete()?.await() }
        purge.request() // A16-f
    }

    override suspend fun reauthenticate(password: String) {
        firebaseCall {
            val user = auth.currentUser ?: throw GatewayError.InvalidCredentials()
            val email = user.email ?: throw GatewayError.InvalidCredentials()
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        }
    }

    override fun signOut() {
        purge.request() // A16-f: el siguiente arranque empieza sin la caché de esta cuenta
        auth.signOut()
    }
}
