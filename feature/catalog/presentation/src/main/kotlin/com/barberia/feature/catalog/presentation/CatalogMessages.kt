package com.barberia.feature.catalog.presentation

import androidx.annotation.StringRes
import com.barberia.feature.catalog.domain.model.CatalogError
import com.barberia.feature.catalog.domain.model.CatalogFailure
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@StringRes
fun CatalogError.messageRes(): Int = when (this) {
    CatalogError.NAME_REQUIRED -> R.string.catalog_error_name
    CatalogError.NAME_TOO_LONG -> R.string.catalog_error_name_long
    CatalogError.NAME_DUPLICATED -> R.string.catalog_error_duplicated
    CatalogError.DURATION_INVALID -> R.string.catalog_error_duration
    CatalogError.PRICE_INVALID -> R.string.catalog_error_price
    CatalogError.TOO_MANY_ITEMS -> R.string.catalog_error_too_many
    CatalogError.SCHEDULE_RANGE_INVALID -> R.string.catalog_error_range
    CatalogError.SCHEDULE_OVERLAP -> R.string.catalog_error_overlap
}

@StringRes
fun CatalogFailure.messageRes(): Int = when (this) {
    CatalogFailure.NoConnection -> R.string.catalog_failure_connection
    CatalogFailure.NotOwner -> R.string.catalog_failure_owner
    else -> R.string.catalog_failure_unknown
}

private val SPANISH = Locale.forLanguageTag("es-MX")

fun DayOfWeek.label(): String = getDisplayName(TextStyle.FULL, SPANISH).replaceFirstChar { it.uppercase() }
