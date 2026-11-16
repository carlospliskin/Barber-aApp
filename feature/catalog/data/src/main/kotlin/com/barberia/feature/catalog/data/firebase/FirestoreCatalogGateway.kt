package com.barberia.feature.catalog.data.firebase

import com.barberia.core.firebase.firebaseCall
import com.barberia.core.firebase.toGatewayError
import com.barberia.feature.catalog.data.gateway.CatalogGateway
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Un solo documento por barbería. El listener cuesta 1 lectura al abrir y 1 por cada cambio;
 * sin red sirve la caché (gratis). Las transacciones necesitan red: sin ella falla con NoConnection.
 */
internal class FirestoreCatalogGateway(private val db: FirebaseFirestore) : CatalogGateway {

    private fun doc(tenantId: String) = db.collection("tenants").document(tenantId).collection("catalog").document("main")

    override fun observe(tenantId: String): Flow<Map<String, Any?>?> = callbackFlow {
        val registration = doc(tenantId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error.toGatewayError()) else trySend(snapshot?.data)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun transact(tenantId: String, update: (Map<String, Any?>?) -> Map<String, Any?>?) {
        firebaseCall {
            val ref = doc(tenantId)
            db.runTransaction { tx ->
                val current = tx.get(ref).data
                update(current)?.let { tx.set(ref, it + ("updatedAt" to FieldValue.serverTimestamp())) }
            }.await()
        }
    }
}
