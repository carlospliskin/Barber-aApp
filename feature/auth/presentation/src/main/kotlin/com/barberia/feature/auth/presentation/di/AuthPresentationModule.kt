package com.barberia.feature.auth.presentation.di

import com.barberia.feature.auth.presentation.account.DeleteAccountViewModel
import com.barberia.feature.auth.presentation.account.StaffAccessViewModel
import com.barberia.feature.auth.presentation.login.LoginViewModel
import com.barberia.feature.auth.presentation.mode.ModeSelectorViewModel
import com.barberia.feature.auth.presentation.register.RegisterViewModel
import com.barberia.feature.auth.presentation.session.SessionViewModel
import com.barberia.feature.auth.presentation.verify.VerifyEmailViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authPresentationModule = module {
    viewModelOf(::SessionViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::ModeSelectorViewModel)
    viewModelOf(::VerifyEmailViewModel)
    viewModelOf(::DeleteAccountViewModel)
    viewModelOf(::StaffAccessViewModel)
}
