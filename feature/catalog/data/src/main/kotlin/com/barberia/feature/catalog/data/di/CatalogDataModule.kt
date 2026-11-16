package com.barberia.feature.catalog.data.di

import com.barberia.feature.catalog.data.firebase.FirestoreCatalogGateway
import com.barberia.feature.catalog.data.firebase.FirestoreInviteGateway
import com.barberia.feature.catalog.data.gateway.CatalogGateway
import com.barberia.feature.catalog.data.gateway.InviteGateway
import com.barberia.feature.catalog.data.repository.FirestoreCatalogRepository
import com.barberia.feature.catalog.data.repository.FirestoreInviteRepository
import com.barberia.feature.catalog.domain.repository.CatalogRepository
import com.barberia.feature.catalog.domain.repository.InviteRepository
import org.koin.dsl.module

val catalogDataModule = module {
    single<CatalogGateway> { FirestoreCatalogGateway(get()) }
    single<CatalogRepository> { FirestoreCatalogRepository(get()) }
    single<InviteGateway> { FirestoreInviteGateway(get()) }
    single<InviteRepository> { FirestoreInviteRepository(get()) }
}
