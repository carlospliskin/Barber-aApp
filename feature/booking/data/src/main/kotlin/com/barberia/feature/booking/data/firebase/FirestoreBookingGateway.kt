package com.barberia.feature.booking.data.firebase

import com.barberia.core.firebase.GatewayError
import com.barberia.core.firebase.firebaseCall
import com.barberia.core.firebase.toGatewayError
import com.barberia.feature.booking.data.gateway.BookingGateway
import com.barberia.feature.booking.data.gateway.SlotTakenException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant

/**
 * Costos (plan Spark):
 * - ver disponibilidad de un día: 1 lectura;
 * - reservar: 1 lectura + 2 escrituras (+ 2 lecturas de reglas);
 * - mis citas en vivo: 1 lectura por cita al abrir y 1 por cambio (máx. 50).
 */
internal class FirestoreBookingGateway(private val db: FirebaseFirestore) : BookingGateway {

    private fun tenantRef(id: String) = db.collection("tenants").document(id)

    override suspend fun getDay(tenantId: String, dayId: String): Map<String, Any?>? = firebaseCall {
        tenantRef(tenantId).collection("days").document(dayId).get().await().data
    }

    override suspend fun activeAppointments(tenantId: String, clientUid: String) = firebaseCall {
        tenantRef(tenantId).collection("appointments")
            .whereEqualTo("clientUid", clientUid)
            .whereEqualTo("status", "BOOKED")
            .get().await()
            .documents.map { it.id to it.data.orEmpty().fromFirestore() }
    }

    override suspend fun book(
        tenantId: String,
        apptId: String,
        appointment: Map<String, Any?>,
        dayId: String,
        keys: Set<String>,
        clientUid: String?,
        newClient: Map<String, Any?>?,
    ) {
        unwrapSlotTaken {
            firebaseCall {
                val dayRef = tenantRef(tenantId).collection("days").document(dayId)
                val apptRef = tenantRef(tenantId).collection("appointments").document(apptId)
                db.runTransaction { tx ->
                    // N1: una cita sin cuenta no tiene ficha (ni lectura extra)
                    val clientRef = clientUid?.let { tenantRef(tenantId).collection("clients").document(it) }
                    val hasClient = clientRef == null || tx.get(clientRef).exists() // lecturas antes que escrituras
                    @Suppress("UNCHECKED_CAST")
                    val booked = (tx.get(dayRef).get("booked") as? Map<String, Any?>).orEmpty()
                    if (keys.any { it in booked }) throw SlotTakenException()
                    tx.set(apptRef, appointment.toFirestore() + ("createdAt" to FieldValue.serverTimestamp()))
                    tx.set(
                        dayRef,
                        mapOf(
                            "booked" to booked + keys.associateWith { apptId },
                            "lastOp" to apptId,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                    if (!hasClient && clientRef != null && newClient != null) {
                        tx.set(clientRef, newClient + ("updatedAt" to FieldValue.serverTimestamp()))
                    }
                }.await()
            }
        }
    }

    override suspend fun cancel(tenantId: String, apptId: String, dayId: String, keys: Set<String>) {
        firebaseCall {
            val batch = db.batch()
            batch.update(
                tenantRef(tenantId).collection("appointments").document(apptId),
                mapOf("status" to "CANCELLED", "updatedAt" to FieldValue.serverTimestamp()),
            )
            batch.update(
                tenantRef(tenantId).collection("days").document(dayId),
                keys.associate { "booked.$it" to FieldValue.delete() } +
                    mapOf("lastOp" to apptId, "updatedAt" to FieldValue.serverTimestamp()),
            )
            batch.commit().await()
        }
    }

    override fun observeUpcoming(
        tenantId: String,
        clientUid: String,
        fromDate: String,
    ): Flow<List<Pair<String, Map<String, Any?>>>> =
        callbackFlow {
            // R4: solo de hoy en adelante, ordenadas. Índice compuesto en firebase/firestore.indexes.json.
            val registration = tenantRef(tenantId).collection("appointments")
                .whereEqualTo("clientUid", clientUid)
                .whereEqualTo("status", "BOOKED") // U4: las canceladas no se leen (no se pagan)
                .whereGreaterThanOrEqualTo("date", fromDate)
                .orderBy("date")
                .limit(MAX_LISTED)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error.toGatewayError())
                    } else {
                        trySend(snapshot?.documents.orEmpty().map { it.id to it.data.orEmpty().fromFirestore() })
                    }
                }
            awaitClose { registration.remove() }
        }

    override fun observeDay(
        tenantId: String,
        date: String,
        barberId: String?,
    ): Flow<List<Pair<String, Map<String, Any?>>>> = callbackFlow {
        var q = tenantRef(tenantId).collection("appointments").whereEqualTo("date", date)
        if (barberId != null) q = q.whereEqualTo("barberId", barberId) // las reglas exigen este filtro al barbero
        val registration = q.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error.toGatewayError())
            } else {
                trySend(snapshot?.documents.orEmpty().map { it.id to it.data.orEmpty().fromFirestore() })
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setStatus(
        tenantId: String,
        apptId: String,
        status: String,
        clientUid: String?,
        updateClient: ((Map<String, Any?>?) -> Map<String, Any?>)?,
    ) {
        firebaseCall {
            val apptRef = tenantRef(tenantId).collection("appointments").document(apptId)
            val clientRef = clientUid?.let { tenantRef(tenantId).collection("clients").document(it) }
            db.runTransaction { tx ->
                val next = if (clientRef != null && updateClient != null) {
                    updateClient(tx.get(clientRef).data?.fromFirestore()).toFirestore() +
                        ("updatedAt" to FieldValue.serverTimestamp())
                } else {
                    null // N1: cita sin cuenta
                }
                tx.update(apptRef, mapOf("status" to status, "updatedAt" to FieldValue.serverTimestamp()))
                if (clientRef != null && next != null) tx.set(clientRef, next)
            }.await()
        }
    }

    override suspend fun getAppointment(tenantId: String, id: String): Map<String, Any?>? = firebaseCall {
        tenantRef(tenantId).collection("appointments").document(id).get().await().data?.fromFirestore()
    }

    private fun clientRef(tenantId: String, uid: String) = tenantRef(tenantId).collection("clients").document(uid)

    override suspend fun getClient(tenantId: String, uid: String): Map<String, Any?>? = firebaseCall {
        clientRef(tenantId, uid).get().await().data?.fromFirestore()
    }

    override fun observeClient(tenantId: String, uid: String): Flow<Map<String, Any?>?> = callbackFlow {
        val registration = clientRef(tenantId, uid).addSnapshotListener { snapshot, error ->
            if (error != null) close(error.toGatewayError()) else trySend(snapshot?.data?.fromFirestore())
        }
        awaitClose { registration.remove() }
    }

    override fun observeClients(tenantId: String): Flow<List<Pair<String, Map<String, Any?>>>> = callbackFlow {
        val registration = tenantRef(tenantId).collection("clients")
            .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(MAX_CLIENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toGatewayError())
                } else {
                    trySend(snapshot?.documents.orEmpty().map { it.id to it.data.orEmpty().fromFirestore() })
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun updateClient(tenantId: String, uid: String, update: (Map<String, Any?>) -> Map<String, Any?>?) {
        firebaseCall {
            val ref = clientRef(tenantId, uid)
            db.runTransaction { tx ->
                val current = tx.get(ref).data?.fromFirestore() ?: return@runTransaction
                update(current)?.let { tx.set(ref, it.toFirestore() + ("updatedAt" to FieldValue.serverTimestamp())) }
            }.await()
        }
    }

    override suspend fun shareCode(code: String): String? = firebaseCall {
        db.collection("shareCodes").document(code).get().await().getString("tenantId")
    }

    override suspend fun tenant(tenantId: String): Map<String, Any?>? = firebaseCall {
        tenantRef(tenantId).get().await().data
    }

    override suspend fun claimShareCode(tenantId: String, code: String): Boolean = firebaseCall {
        val ref = db.collection("shareCodes").document(code)
        if (ref.get().await().exists()) return@firebaseCall false
        db.batch()
            .set(ref, mapOf("tenantId" to tenantId, "createdAt" to FieldValue.serverTimestamp()))
            .update(tenantRef(tenantId), "shareCode", code)
            .commit().await()
        true
    }

    private companion object {
        const val MAX_LISTED = 20L
        const val MAX_CLIENTS = 50L
    }
}

/** B11: Firebase envuelve la excepción lanzada dentro de la transacción; aquí se recupera. */
internal suspend fun <T> unwrapSlotTaken(block: suspend () -> T): T = try {
    block()
} catch (e: GatewayError) {
    if (generateSequence<Throwable>(e) { it.cause }.any { it is SlotTakenException }) throw SlotTakenException()
    throw e
}

private fun Map<String, Any?>.toFirestore(): Map<String, Any?> =
    mapValues { (_, v) -> if (v is Instant) Timestamp(v.epochSecond, v.nano) else v }

private fun Map<String, Any?>.fromFirestore(): Map<String, Any?> =
    mapValues { (_, v) -> if (v is Timestamp) Instant.ofEpochSecond(v.seconds, v.nanoseconds.toLong()) else v }
