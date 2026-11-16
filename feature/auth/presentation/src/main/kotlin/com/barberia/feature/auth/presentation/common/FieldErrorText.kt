package com.barberia.feature.auth.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.barberia.feature.auth.domain.model.FieldError

/** Primer error de los campos indicados, ya como texto. */
@Composable
fun Set<FieldError>.textFor(vararg fields: FieldError): String? = fields.firstOrNull { it in this }?.let { stringResource(it.messageRes()) }
