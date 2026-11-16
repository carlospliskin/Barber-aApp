package com.barberia.app.di

import com.barberia.app.home.HomeViewModel
import com.barberia.feature.auth.data.di.ApplicationScope
import com.barberia.feature.auth.domain.usecase.CheckEmailVerifiedUseCase
import com.barberia.feature.auth.domain.usecase.ObserveCurrentUserUseCase
import com.barberia.feature.auth.domain.usecase.ResendVerificationEmailUseCase
import com.barberia.feature.auth.domain.usecase.ObserveSessionDestinationUseCase
import com.barberia.feature.auth.domain.usecase.RegisterClientUseCase
import com.barberia.feature.auth.domain.usecase.RegisterOwnerUseCase
import com.barberia.feature.auth.domain.usecase.RetrySessionUseCase
import com.barberia.feature.auth.domain.usecase.SelectModeUseCase
import com.barberia.feature.auth.domain.usecase.SendPasswordResetUseCase
import com.barberia.feature.auth.domain.usecase.SignInUseCase
import com.barberia.feature.auth.domain.usecase.SignOutUseCase
import com.barberia.feature.auth.domain.usecase.DeleteAccountUseCase
import com.barberia.feature.auth.domain.repository.UserDataEraser
import com.barberia.feature.booking.domain.usecase.EraseUserDataUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Composition root: lo que vive toda la app. */
val appModule = module {
    single<CoroutineScope>(ApplicationScope) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    viewModelOf(::HomeViewModel)
}

/** Casos de uso de auth: el dominio es Kotlin puro y no depende de Koin, por eso se registran aquí. */
val authDomainModule = module {
    factoryOf(::SignInUseCase)
    factoryOf(::RegisterOwnerUseCase)
    factoryOf(::RegisterClientUseCase)
    factoryOf(::SendPasswordResetUseCase)
    factoryOf(::SelectModeUseCase)
    factoryOf(::SignOutUseCase)
    factoryOf(::ObserveSessionDestinationUseCase)
    factoryOf(::ObserveCurrentUserUseCase)
    factoryOf(::RetrySessionUseCase)
    factoryOf(::ResendVerificationEmailUseCase)
    factoryOf(::CheckEmailVerifiedUseCase)
    // A7: eliminar cuenta (el borrado de datos lo hace booking)
    factoryOf(::DeleteAccountUseCase)
    factoryOf(::EraseUserDataUseCase)
    factory<UserDataEraser> { BookingUserDataEraser(get()) }
}
