package com.barberia.feature.catalog.data.di

import com.barberia.feature.catalog.data.firebase.FirestoreCatalogGateway
import com.barberia.feature.catalog.data.gateway.CatalogGateway
import com.barberia.feature.catalog.data.repository.FirestoreCatalogRepository
import com.barberia.feature.catalog.domain.repository.CatalogRepository
import org.koin.dsl.module

val catalogDataModule = module {
    single<CatalogGateway> { FirestoreCatalogGateway(get()) }
    single<CatalogRepository> { FirestoreCatalogRepository(get()) }
}
