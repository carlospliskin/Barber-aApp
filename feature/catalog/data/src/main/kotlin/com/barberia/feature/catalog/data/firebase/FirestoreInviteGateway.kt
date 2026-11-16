package com.barberia.feature.catalog.data.firebase

import com.barberia.core.firebase.firebaseCall
import com.barberia.feature.catalog.data.gateway.InviteGateway
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import kotlinx.coroutines.tasks.await

internal class FirestoreInviteGateway(private val db: FirebaseFirestore) : InviteGateway {
    private fun ref(code: String) = db.collection("staffInvites").document(code)

    override suspend fun get(code: String): Map<String, Any?>? = firebaseCall {
        ref(code).get().await().data?.mapValues { (_, v) ->
            if (v is Timestamp) Instant.ofEpochSecond(v.seconds, v.nanoseconds.toLong()) else v
        }
    }

    override suspend fun createIfAbsent(code: String, data: Map<String, Any?>): Boolean = firebaseCall {
        if (ref(code).get().await().exists()) return@firebaseCall false
        val firestoreData = data.mapValues { (_, v) -> if (v is Instant) Timestamp(v.epochSecond, v.nano) else v }
        ref(code).set(firestoreData + ("createdAt" to FieldValue.serverTimestamp())).await()
        true
    }

    override suspend fun redeem(code: String, uid: String, profileChanges: Map<String, Any?>) {
        firebaseCall {
            val batch = db.batch()
                .update(db.collection("users").document(uid), profileChanges)
                .update(ref(code), "usedBy", uid)
            // A16: si el dueño le había quitado el acceso, una invitación nueva se lo devuelve (la regla lo verifica).
            (profileChanges["staffTenantId"] as? String)?.let { tenant ->
                batch.delete(db.collection("tenants").document(tenant).collection("revoked").document(uid))
            }
            batch.commit().await()
        }
    }

    override suspend fun invitesOfTenant(tenantId: String): List<Map<String, Any?>> = firebaseCall {
        db.collection("staffInvites").whereEqualTo("tenantId", tenantId).get().await().documents.map { it.data.orEmpty() }
    }

    override suspend fun revoke(tenantId: String, uid: String, barberId: String) {
        firebaseCall {
            db.collection("tenants").document(tenantId).collection("revoked").document(uid)
                .set(mapOf("barberId" to barberId, "createdAt" to FieldValue.serverTimestamp())).await()
        }
    }
}
