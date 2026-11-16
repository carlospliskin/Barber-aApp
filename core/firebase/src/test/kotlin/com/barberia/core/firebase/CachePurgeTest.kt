package com.barberia.core.firebase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A16-f: al salir de una cuenta, la caché de Firestore se borra en el siguiente arranque (antes de usarla). */
class CachePurgeTest {
    private class MemoryFlag : PurgeFlag {
        var value = false
        override fun get() = value
        override fun set(value: Boolean) {
            this.value = value
        }
    }

    @Test fun `sin pedirlo no se borra (la cache ahorra lecturas)`() {
        assertFalse(CachePurge(MemoryFlag()).consume())
    }

    @Test fun `se pide al salir y se cumple una sola vez`() {
        val purge = CachePurge(MemoryFlag())
        purge.request()
        assertTrue(purge.consume())
        assertFalse(purge.consume())
    }
}
