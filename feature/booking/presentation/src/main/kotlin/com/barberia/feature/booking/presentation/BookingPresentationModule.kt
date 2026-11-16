package com.barberia.feature.booking.presentation

import com.barberia.feature.booking.domain.model.BookingPolicy
import com.barberia.feature.booking.domain.usecase.BookAppointmentUseCase
import com.barberia.feature.booking.domain.usecase.CancelAppointmentUseCase
import com.barberia.feature.booking.domain.usecase.EnsureShareCodeUseCase
import com.barberia.feature.booking.domain.usecase.GetAvailableSlotsUseCase
import com.barberia.feature.booking.domain.usecase.JoinBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.LeaveBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.ObserveJoinedBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.ObserveMyAppointmentsUseCase
import com.barberia.feature.booking.presentation.book.BookingViewModel
import com.barberia.feature.booking.presentation.client.ClientHomeViewModel
import com.barberia.feature.booking.presentation.owner.ShareCodeViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.security.SecureRandom
import java.time.Clock
import java.util.Random

val bookingPresentationModule = module {
    single<Clock> { Clock.systemUTC() }
    single { BookingPolicy() }
    single<Random> { SecureRandom() }
    factoryOf(::GetAvailableSlotsUseCase)
    factoryOf(::BookAppointmentUseCase)
    factoryOf(::CancelAppointmentUseCase)
    factoryOf(::ObserveMyAppointmentsUseCase)
    factoryOf(::JoinBarbershopUseCase)
    factoryOf(::ObserveJoinedBarbershopUseCase)
    factoryOf(::LeaveBarbershopUseCase)
    factoryOf(::EnsureShareCodeUseCase)
    viewModelOf(::ClientHomeViewModel)
    viewModelOf(::BookingViewModel)
    viewModelOf(::ShareCodeViewModel)
}
