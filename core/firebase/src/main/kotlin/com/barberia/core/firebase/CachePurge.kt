package com.barberia.core.firebase

import android.content.Context

/** Marca persistente (sobrevive a cerrar la app). */
interface PurgeFlag {
    fun get(): Boolean
    fun set(value: Boolean)
}

/**
 * A16-f: Firestore solo deja borrar su caché antes de usarse. Al cerrar sesión, soltar el rol o borrar la
 * cuenta se pide ([request]) y se cumple en el siguiente arranque ([consume]) antes de la primera consulta.
 * Así otra cuenta en el mismo teléfono (o un barbero revocado) no conserva citas de la anterior.
 */
class CachePurge(private val flag: PurgeFlag) {
    fun request() = flag.set(true)

    /** `true` una sola vez si se pidió. */
    fun consume(): Boolean {
        if (!flag.get()) return false
        flag.set(false)
        return true
    }
}

internal class SharedPrefsPurgeFlag(context: Context) : PurgeFlag {
    private val prefs = context.getSharedPreferences("firestore_cache", Context.MODE_PRIVATE)
    override fun get() = prefs.getBoolean(KEY, false)
    override fun set(value: Boolean) {
        prefs.edit().putBoolean(KEY, value).commit() // commit: debe quedar escrito aunque el proceso muera ya
    }

    private companion object {
        const val KEY = "purge_on_start"
    }
}
