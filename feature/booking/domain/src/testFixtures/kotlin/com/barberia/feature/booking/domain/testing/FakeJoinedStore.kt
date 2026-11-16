package com.barberia.feature.booking.domain.testing

import com.barberia.feature.booking.domain.repository.JoinedBarbershopStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeJoinedStore : JoinedBarbershopStore {
    private val all = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Acceso directo para preparar tests: `saved["uid"] = "t1"`. */
    val saved = object : AbstractMutableMap<String, String>() {
        override val entries get() = all.value.toMutableMap().entries
        override fun put(key: String, value: String): String? {
            val old = all.value[key]
            all.value = all.value + (key to value)
            return old
        }
        override fun get(key: String): String? = all.value[key]
    }

    override fun tenantId(uid: String): Flow<String?> = all.map { it[uid] }

    override suspend fun save(uid: String, tenantId: String?) {
        all.value = if (tenantId == null) all.value - uid else all.value + (uid to tenantId)
    }
}
