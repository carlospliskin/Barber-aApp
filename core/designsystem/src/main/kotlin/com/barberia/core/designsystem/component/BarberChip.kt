package com.barberia.core.designsystem.component

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Chip de selección del tema. Sin seleccionar usa texto claro (onSurface) para que no se confunda
 * con un chip deshabilitado (U5: con los colores por defecto se veían iguales sobre fondo oscuro).
 */
@Composable
fun BarberChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        enabled = enabled,
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}
