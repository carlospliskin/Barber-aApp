package com.barberia.feature.auth.data.firebase

import com.barberia.core.firebase.firebaseCall
import com.barberia.core.firebase.CachePurge
import com.barberia.feature.auth.data.gateway.ProfileGateway
import com.barberia.feature.auth.data.gateway.ProfileRecord
import com.barberia.feature.auth.data.gateway.TenantRecord
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * `users/{uid}` y `tenants/{ownerUid}`.
 * `get()` usa el servidor y, sin red, la caché local (las lecturas de caché no se cobran).
 */
internal class FirestoreProfileGateway(
    private val db: FirebaseFirestore,
    private val purge: CachePurge,
) : ProfileGateway {

    override suspend fun fetch(uid: String): ProfileRecord? = firebaseCall {
        val doc = db.collection(USERS).document(uid).get().await()
        if (!doc.exists()) return@firebaseCall null
        ProfileRecord(
            displayName = doc.getString("displayName").orEmpty(),
            email = doc.getString("email").orEmpty(),
            phone = doc.getString("phone"),
            staffTenantId = doc.getString("staffTenantId"),
            staffRole = doc.getString("staffRole"),
            staffBarberId = doc.getString("staffBarberId"),
        )
    }

    override suspend fun createOwner(uid: String, profile: ProfileRecord, tenant: TenantRecord) = firebaseCall {
        db.batch()
            .set(db.collection(USERS).document(uid), profile.toMap())
            .set(
                db.collection(TENANTS).document(uid),
                mapOf(
                    "name" to tenant.name,
                    "ownerUid" to tenant.ownerUid,
                    "timezone" to tenant.timezone,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            .commit()
            .await()
        Unit
    }

    override suspend fun createClient(uid: String, profile: ProfileRecord) = firebaseCall {
        db.collection(USERS).document(uid).set(profile.toMap()).await()
        Unit
    }

    override suspend fun delete(uid: String) = firebaseCall {
        db.collection(USERS).document(uid).delete().await()
        Unit
    }

    override suspend fun tenantExists(tenantId: String): Boolean = firebaseCall {
        db.collection(TENANTS).document(tenantId).get().await().exists()
    }

    override suspend fun isRevoked(tenantId: String, uid: String): Boolean = firebaseCall {
        db.collection(TENANTS).document(tenantId).collection("revoked").document(uid).get().await().exists()
    }

    override suspend fun leaveStaff(uid: String) = firebaseCall {
        db.collection(USERS).document(uid).update(
            mapOf("staffTenantId" to null, "staffRole" to null, "staffBarberId" to null, "staffInviteCode" to null),
        ).await()
        purge.request() // A16-f: la agenda que vio como barbero no se queda en el teléfono
        Unit
    }

    private fun ProfileRecord.toMap(): Map<String, Any?> = mapOf(
        "displayName" to displayName,
        "email" to email,
        "phone" to phone,
        "staffTenantId" to staffTenantId,
        "staffRole" to staffRole,
        "createdAt" to FieldValue.serverTimestamp(),
    )

    private companion object {
        const val USERS = "users"
        const val TENANTS = "tenants"
    }
}
