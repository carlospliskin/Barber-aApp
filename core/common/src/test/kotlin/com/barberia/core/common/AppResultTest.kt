package com.barberia.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppResultTest {
    @Test fun `map transforma solo el exito`() {
        val ok: AppResult<String, Int> = AppResult.Success(2)
        val ko: AppResult<String, Int> = AppResult.Failure("x")
        assertEquals(AppResult.Success(4), ok.map { it * 2 })
        assertEquals(AppResult.Failure("x"), ko.map { it * 2 })
    }

    @Test fun `fold y getOrNull`() {
        val ko: AppResult<String, Int> = AppResult.Failure("boom")
        assertEquals("boom", ko.fold({ it }, { "ok" }))
        assertNull(ko.getOrNull())
    }
}
