package com.barberia.feature.booking.data.di

import com.barberia.feature.booking.data.firebase.FirestoreBookingGateway
import com.barberia.feature.booking.data.gateway.BookingGateway
import com.barberia.feature.booking.data.repository.FirestoreBookingRepository
import com.barberia.feature.booking.domain.repository.BookingRepository
import org.koin.dsl.module

val bookingDataModule = module {
    single<BookingGateway> { FirestoreBookingGateway(get()) }
    single<BookingRepository> { FirestoreBookingRepository(get()) }
}
