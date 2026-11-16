package com.barberia.feature.catalog.domain

import com.barberia.core.common.AppResult
import com.barberia.feature.catalog.domain.editor.CatalogEditor
import com.barberia.feature.catalog.domain.model.Catalog
import com.barberia.feature.catalog.domain.model.CatalogError
import com.barberia.feature.catalog.domain.model.CatalogFailure
import com.barberia.feature.catalog.domain.model.Money
import com.barberia.feature.catalog.domain.model.Service
import com.barberia.feature.catalog.domain.model.ServiceDraft
import com.barberia.feature.catalog.domain.testing.FakeCatalogRepository
import com.barberia.feature.catalog.domain.usecase.AddServiceUseCase
import com.barberia.feature.catalog.domain.usecase.SetServiceActiveUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogUseCasesTest {
    private val editor = CatalogEditor { "nuevo" }

    @Test fun `C9 la edicion se aplica sobre la version mas reciente, no sobre la que vio la pantalla`() = runTest {
        // Otro teléfono del dueño ya agregó "Corte" (versión 3); esta pantalla todavía no lo ve.
        val latest = Catalog(listOf(Service("a", "Corte", 30, Money(15_000))), emptyList(), version = 3)
        val repo = FakeCatalogRepository(latest)

        val result = AddServiceUseCase(repo, editor)("t1", ServiceDraft("corte", 30, Money(15_000)))

        assertEquals(
            AppResult.Failure(CatalogFailure.Validation(setOf(CatalogError.NAME_DUPLICATED))),
            result,
        )
        assertEquals(3, repo.stored.value.version) // no se guardó nada
    }

    @Test fun `guardar sube la version`() = runTest {
        val repo = FakeCatalogRepository()
        AddServiceUseCase(repo, editor)("t1", ServiceDraft("Corte", 30, Money(15_000)))
        assertEquals(1, repo.stored.value.version)
        SetServiceActiveUseCase(repo, editor)("t1", "nuevo", false)
        assertEquals(2, repo.stored.value.version)
    }

    @Test fun `un error del repositorio llega tal cual`() = runTest {
        val repo = FakeCatalogRepository().apply { failure = CatalogFailure.NoConnection }
        assertEquals(
            AppResult.Failure(CatalogFailure.NoConnection),
            AddServiceUseCase(repo, editor)("t1", ServiceDraft("Corte", 30, Money(0))),
        )
    }
}
