package com.barberia.feature.booking.data.di

import com.barberia.feature.booking.data.firebase.FirestoreBookingGateway
import com.barberia.feature.booking.data.gateway.BookingGateway
import com.barberia.feature.booking.data.repository.FirestoreBookingRepository
import com.barberia.feature.booking.data.local.DataStoreJoinedStore
import com.barberia.feature.booking.domain.repository.BookingRepository
import com.barberia.feature.booking.domain.repository.JoinedBarbershopStore
import com.barberia.feature.booking.domain.repository.ReminderScheduler
import com.barberia.feature.booking.data.reminder.WorkManagerReminderScheduler
import com.barberia.feature.booking.data.firebase.FirestoreAccountDataGateway
import com.barberia.feature.booking.data.gateway.AccountDataGateway
import com.barberia.feature.booking.data.repository.FirestoreAccountDataRepository
import com.barberia.feature.booking.domain.repository.AccountDataRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val bookingDataModule = module {
    single<BookingGateway> { FirestoreBookingGateway(get()) }
    single<BookingRepository> { FirestoreBookingRepository(get()) }
    single<AccountDataGateway> { FirestoreAccountDataGateway(get()) }
    single<AccountDataRepository> { FirestoreAccountDataRepository(get()) }
    single<JoinedBarbershopStore> { DataStoreJoinedStore(androidContext()) }
    single<ReminderScheduler> { WorkManagerReminderScheduler(androidContext()) }
}
