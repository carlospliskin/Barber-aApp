package com.barberia.feature.catalog.presentation

import com.barberia.feature.catalog.domain.editor.CatalogEditor
import com.barberia.feature.catalog.domain.editor.IdGenerator
import com.barberia.feature.catalog.domain.usecase.AddBarberUseCase
import com.barberia.feature.catalog.domain.usecase.AddServiceUseCase
import com.barberia.feature.catalog.domain.usecase.AddTimeOffUseCase
import com.barberia.feature.catalog.domain.usecase.CreateBarberInviteUseCase
import com.barberia.feature.catalog.domain.usecase.ObserveCatalogUseCase
import com.barberia.feature.catalog.domain.usecase.RemoveTimeOffUseCase
import com.barberia.feature.catalog.domain.usecase.RenameBarberUseCase
import com.barberia.feature.catalog.domain.usecase.RevokeBarberAccessUseCase
import com.barberia.feature.catalog.domain.usecase.SetBarberActiveUseCase
import com.barberia.feature.catalog.domain.usecase.SetBarberScheduleUseCase
import com.barberia.feature.catalog.domain.usecase.SetServiceActiveUseCase
import com.barberia.feature.catalog.domain.usecase.UpdateServiceUseCase
import java.util.UUID
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Casos de uso + ViewModel del catálogo. El editor genera ids con UUID. */
val catalogPresentationModule = module {
    single<IdGenerator> { IdGenerator { UUID.randomUUID().toString() } }
    singleOf(::CatalogEditor)
    factoryOf(::ObserveCatalogUseCase)
    factoryOf(::AddServiceUseCase)
    factoryOf(::UpdateServiceUseCase)
    factoryOf(::SetServiceActiveUseCase)
    factoryOf(::AddBarberUseCase)
    factoryOf(::RenameBarberUseCase)
    factoryOf(::SetBarberActiveUseCase)
    factoryOf(::SetBarberScheduleUseCase)
    factoryOf(::CreateBarberInviteUseCase)
    factoryOf(::AddTimeOffUseCase)
    factoryOf(::RemoveTimeOffUseCase)
    factoryOf(::RevokeBarberAccessUseCase)
    viewModelOf(::CatalogViewModel)
}
