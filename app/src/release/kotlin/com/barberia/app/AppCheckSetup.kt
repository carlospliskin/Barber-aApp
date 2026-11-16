package com.barberia.app

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * V3: App Check con Play Integrity (gratis en Spark). Firestore solo acepta peticiones de la app real
 * cuando en la consola de Firebase se activa "Enforce" (primero en modo monitor, ver docs/FIREBASE.md).
 */
fun appCheckInstaller(): ((FirebaseApp) -> Unit)? = { app ->
    FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
