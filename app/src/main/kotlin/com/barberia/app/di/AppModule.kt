package com.barberia.app.di

import com.barberia.app.BuildConfig
import com.barberia.app.home.HomeViewModel
import com.barberia.core.network.GraphQLClient
import com.barberia.core.network.OkHttpGraphQLClient
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Composition root: aquí se eligen las implementaciones concretas. */
val appModule = module {
    single<GraphQLClient> { OkHttpGraphQLClient(endpoint = BuildConfig.GRAPHQL_URL) }
    viewModelOf(::HomeViewModel)
}
