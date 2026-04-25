package com.example.moasis.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = EmergencyRed,
    background = NightSurface,
    surface = NightPanel,
    surfaceVariant = Color(0xFF232C33),
    onPrimary = NightText,
    onBackground = NightText,
    onSurface = NightText,
    onSurfaceVariant = Color(0xFFC2CDD4),
    errorContainer = Color(0xFF4A201A),
    onErrorContainer = Color(0xFFF8D6D1),
)

private val LightColorScheme = lightColorScheme(
    primary = EmergencyRed,
    background = SandSurface,
    surface = PanelLight,
    surfaceVariant = PanelMuted,
    onPrimary = Color.White,
    onBackground = SlateText,
    onSurface = SlateText,
    onSurfaceVariant = SlateMuted,
    errorContainer = EmergencyRedSoft,
    onErrorContainer = EmergencyRed,
)

@Composable
fun MoasisTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
