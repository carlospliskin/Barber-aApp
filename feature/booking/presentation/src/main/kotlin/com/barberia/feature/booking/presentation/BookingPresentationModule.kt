package com.barberia.feature.booking.presentation

import com.barberia.feature.booking.domain.model.BookingPolicy
import com.barberia.feature.booking.domain.model.LoyaltyPolicy
import com.barberia.feature.booking.domain.usecase.BookAppointmentUseCase
import com.barberia.feature.booking.domain.usecase.BookWalkInUseCase
import com.barberia.feature.booking.domain.usecase.CancelAppointmentUseCase
import com.barberia.feature.booking.domain.usecase.CancelByStaffUseCase
import com.barberia.feature.booking.domain.usecase.CancelReminderUseCase
import com.barberia.feature.booking.domain.usecase.EnsureShareCodeUseCase
import com.barberia.feature.booking.domain.usecase.ForgivePenaltyUseCase
import com.barberia.feature.booking.domain.usecase.GetAvailableSlotsUseCase
import com.barberia.feature.booking.domain.usecase.GetBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.JoinBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.LeaveBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.MarkCompletedUseCase
import com.barberia.feature.booking.domain.usecase.MarkNoShowUseCase
import com.barberia.feature.booking.domain.usecase.ObserveAgendaUseCase
import com.barberia.feature.booking.domain.usecase.ObserveClientsUseCase
import com.barberia.feature.booking.domain.usecase.ObserveJoinedBarbershopUseCase
import com.barberia.feature.booking.domain.usecase.ObserveMyAppointmentsUseCase
import com.barberia.feature.booking.domain.usecase.ObserveMyLoyaltyUseCase
import com.barberia.feature.booking.domain.usecase.RedeemRewardUseCase
import com.barberia.feature.booking.domain.usecase.SyncRemindersUseCase
import com.barberia.feature.booking.domain.usecase.UndoMarkUseCase
import com.barberia.feature.booking.presentation.agenda.AgendaViewModel
import com.barberia.feature.booking.presentation.book.BookingViewModel
import com.barberia.feature.booking.presentation.client.ClientHomeViewModel
import com.barberia.feature.booking.presentation.clients.ClientsViewModel
import com.barberia.feature.booking.presentation.invite.RedeemInviteViewModel
import com.barberia.feature.booking.presentation.loyalty.LoyaltyViewModel
import com.barberia.feature.booking.presentation.owner.ShareCodeViewModel
import com.barberia.feature.booking.presentation.walkin.WalkInViewModel
import com.barberia.feature.catalog.domain.usecase.RedeemBarberInviteUseCase
import java.security.SecureRandom
import java.time.Clock
import java.util.Random
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

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

    // Fase 6: agenda e invitaciones
    factoryOf(::GetBarbershopUseCase)
    factoryOf(::ObserveAgendaUseCase)
    factoryOf(::MarkCompletedUseCase)
    factory { MarkNoShowUseCase(get(), get(), get()) }
    factoryOf(::CancelByStaffUseCase)
    factoryOf(::UndoMarkUseCase) // F7-2
    factoryOf(::RedeemBarberInviteUseCase)
    viewModelOf(::AgendaViewModel)
    viewModelOf(::RedeemInviteViewModel)

    // Fase 7: clientes, lealtad y penalizaciones
    single { LoyaltyPolicy() }
    factoryOf(::RedeemRewardUseCase)
    factoryOf(::ForgivePenaltyUseCase)
    factoryOf(::ObserveClientsUseCase)
    factoryOf(::ObserveMyLoyaltyUseCase)
    viewModelOf(::ClientsViewModel)

    // Fase 8: recordatorios
    factoryOf(::SyncRemindersUseCase)
    factoryOf(::CancelReminderUseCase)
    viewModelOf(::LoyaltyViewModel)

    // N1: agendar sin cuenta
    factoryOf(::BookWalkInUseCase)
    viewModelOf(::WalkInViewModel)
}
