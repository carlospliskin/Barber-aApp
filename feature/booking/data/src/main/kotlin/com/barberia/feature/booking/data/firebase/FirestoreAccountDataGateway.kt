package com.barberia.feature.booking.data.firebase

import com.barberia.core.firebase.firebaseCall
import com.barberia.feature.booking.data.gateway.AccountDataGateway
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

internal class FirestoreAccountDataGateway(private val db: FirebaseFirestore) : AccountDataGateway {

    override suspend fun appointmentsOfClient(uid: String, limit: Int) = firebaseCall {
        // Necesita el índice de grupo de `clientUid` (firestore.indexes.json → fieldOverrides).
        db.collectionGroup("appointments").whereEqualTo("clientUid", uid).limit(limit.toLong()).get().await()
            .documents.mapNotNull { d ->
                val tenant = d.reference.parent.parent?.id ?: return@mapNotNull null
                Triple(tenant, d.id, d.data.orEmpty().fromFirestore())
            }
    }

    override suspend fun anonymize(tenantId: String, ids: List<String>) = firebaseCall {
        val batch = db.batch()
        ids.forEach { id ->
            batch.update(
                db.collection("tenants").document(tenantId).collection("appointments").document(id),
                mapOf("clientUid" to null, "clientName" to "", "updatedAt" to FieldValue.serverTimestamp())
            )
        }
        batch.commit().await()
        Unit
    }

    override suspend fun tenantAppointments(tenantId: String, limit: Int) = firebaseCall {
        db.collection("tenants").document(tenantId).collection("appointments").limit(limit.toLong()).get().await()
            .documents.map { it.id to it.data.orEmpty() }
    }

    override suspend fun clientIds(tenantId: String, limit: Int) = firebaseCall {
        db.collection("tenants").document(tenantId).collection("clients").limit(limit.toLong()).get().await()
            .documents.map { it.id }
    }

    override suspend fun inviteCodes(tenantId: String) = firebaseCall {
        db.collection("staffInvites").whereEqualTo("tenantId", tenantId).get().await().documents.map { it.id }
    }

    override suspend fun revokedIds(tenantId: String) = firebaseCall {
        db.collection("tenants").document(tenantId).collection("revoked").get().await().documents.map { it.id }
    }

    override suspend fun shareCodeOf(tenantId: String) = firebaseCall {
        db.collection("tenants").document(tenantId).get().await().getString("shareCode")
    }

    override suspend fun deleteAll(paths: List<String>) = firebaseCall {
        if (paths.isEmpty()) return@firebaseCall
        val batch = db.batch()
        paths.forEach { batch.delete(db.document(it)) }
        batch.commit().await()
        Unit
    }
}
