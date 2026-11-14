package com.barberia.feature.auth.presentation.di

import com.barberia.feature.auth.domain.usecase.GetCurrentUserUseCase
import com.barberia.feature.auth.domain.usecase.LoginUseCase
import com.barberia.feature.auth.domain.usecase.LogoutUseCase
import com.barberia.feature.auth.domain.usecase.RegisterClientUseCase
import com.barberia.feature.auth.domain.usecase.RegisterOwnerUseCase
import com.barberia.feature.auth.domain.usecase.RestoreSessionUseCase
import com.barberia.feature.auth.domain.usecase.SwitchModeUseCase
import com.barberia.feature.auth.presentation.login.LoginViewModel
import com.barberia.feature.auth.presentation.mode.ModeSelectorViewModel
import com.barberia.feature.auth.presentation.splash.SplashViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val authPresentationModule = module {
    factoryOf(::RestoreSessionUseCase)
    factoryOf(::GetCurrentUserUseCase)
    factoryOf(::LoginUseCase)
    factoryOf(::RegisterOwnerUseCase)
    factoryOf(::RegisterClientUseCase)
    factoryOf(::SwitchModeUseCase)
    factoryOf(::LogoutUseCase)

    viewModelOf(::SplashViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::ModeSelectorViewModel)
}
