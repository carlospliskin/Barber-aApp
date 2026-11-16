package com.barberia.core.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Configuración de Firebase sin `google-services.json`: los valores llegan desde BuildConfig.
 * En debug se usa un proyecto `demo-*` + Emulator Suite: no existe en la nube y no gasta cuota.
 */
data class FirebaseConfig(
    val projectId: String,
    val applicationId: String,
    val apiKey: String,
    val emulatorHost: String?,
    val authPort: Int = 9199,
    val firestorePort: Int = 8085,
) {
    val usesEmulator: Boolean get() = emulatorHost != null
}

fun firebaseModule(config: FirebaseConfig) = module {
    single { initFirebase(androidContext(), config) }
    single {
        FirebaseAuth.getInstance(get<FirebaseApp>()).apply {
            config.emulatorHost?.let { useEmulator(it, config.authPort) }
        }
    }
    single {
        FirebaseFirestore.getInstance(get<FirebaseApp>()).apply {
            config.emulatorHost?.let { useEmulator(it, config.firestorePort) }
            // Caché persistente: las lecturas que salen de caché no se cobran.
            firestoreSettings = firestoreSettings {
                setLocalCacheSettings(persistentCacheSettings { setSizeBytes(CACHE_BYTES) })
            }
        }
    }
}

private const val CACHE_BYTES = 50L * 1024 * 1024

private fun initFirebase(context: Context, config: FirebaseConfig): FirebaseApp {
    FirebaseApp.getApps(context).firstOrNull()?.let { return it }
    require(config.projectId.isNotBlank() && config.applicationId.isNotBlank()) {
        "Falta la configuración de Firebase para este build (ver docs/FIREBASE.md)."
    }
    val options = FirebaseOptions.Builder()
        .setProjectId(config.projectId)
        .setApplicationId(config.applicationId)
        .setApiKey(config.apiKey)
        .build()
    return FirebaseApp.initializeApp(context, options)
}
