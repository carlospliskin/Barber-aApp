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

    private fun tenant(id: String) = db.collection("tenants").document(id)

    override suspend fun getDay(tenantId: String, dayId: String): Map<String, Any?>? = firebaseCall {
        tenant(tenantId).collection("days").document(dayId).get().await().data
    }

    override suspend fun activeAppointments(tenantId: String, clientUid: String) = firebaseCall {
        tenant(tenantId).collection("appointments")
            .whereEqualTo("clientUid", clientUid)
            .whereEqualTo("status", "BOOKED")
            .get().await()
            .documents.map { it.data.orEmpty().fromFirestore() }
    }

    override suspend fun book(
        tenantId: String,
        apptId: String,
        appointment: Map<String, Any?>,
        dayId: String,
        keys: Set<String>,
    ) {
        unwrapSlotTaken {
            firebaseCall {
                val dayRef = tenant(tenantId).collection("days").document(dayId)
                val apptRef = tenant(tenantId).collection("appointments").document(apptId)
                db.runTransaction { tx ->
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
                }.await()
            }
        }
    }

    override suspend fun cancel(tenantId: String, apptId: String, dayId: String, keys: Set<String>) {
        firebaseCall {
            val batch = db.batch()
            batch.update(
                tenant(tenantId).collection("appointments").document(apptId),
                mapOf("status" to "CANCELLED", "updatedAt" to FieldValue.serverTimestamp()),
            )
            batch.update(
                tenant(tenantId).collection("days").document(dayId),
                keys.associate { "booked.$it" to FieldValue.delete() } +
                    mapOf("lastOp" to apptId, "updatedAt" to FieldValue.serverTimestamp()),
            )
            batch.commit().await()
        }
    }

    override fun observeClient(tenantId: String, clientUid: String): Flow<List<Pair<String, Map<String, Any?>>>> =
        callbackFlow {
            // Sin orderBy: con un filtro de igualdad no hace falta índice compuesto; se ordena en el repositorio.
            val registration = tenant(tenantId).collection("appointments")
                .whereEqualTo("clientUid", clientUid)
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

    override suspend fun shareCode(code: String): String? = firebaseCall {
        db.collection("shareCodes").document(code).get().await().getString("tenantId")
    }

    private companion object {
        const val MAX_LISTED = 50L
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
