package com.barberia.feature.catalog.data.firebase

import com.barberia.core.firebase.firebaseCall
import com.barberia.feature.catalog.data.gateway.InviteGateway
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.Instant

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
            db.batch()
                .update(db.collection("users").document(uid), profileChanges)
                .update(ref(code), "usedBy", uid)
                .commit().await()
        }
    }
}
