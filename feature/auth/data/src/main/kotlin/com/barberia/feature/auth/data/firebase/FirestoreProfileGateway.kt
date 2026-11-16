package com.barberia.feature.auth.data.firebase

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
internal class FirestoreProfileGateway(private val db: FirebaseFirestore) : ProfileGateway {

    override suspend fun fetch(uid: String): ProfileRecord? = firebaseCall {
        val doc = db.collection(USERS).document(uid).get().await()
        if (!doc.exists()) return@firebaseCall null
        ProfileRecord(
            displayName = doc.getString("displayName").orEmpty(),
            email = doc.getString("email").orEmpty(),
            phone = doc.getString("phone"),
            staffTenantId = doc.getString("staffTenantId"),
            staffRole = doc.getString("staffRole"),
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
