package com.barberia.app

import com.google.firebase.FirebaseApp

/** Debug usa el emulador de Firebase, que no exige App Check. */
fun appCheckInstaller(): ((FirebaseApp) -> Unit)? = null
