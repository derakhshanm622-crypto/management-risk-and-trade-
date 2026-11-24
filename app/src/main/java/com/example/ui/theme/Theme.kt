package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TradeHubDarkColorScheme = darkColorScheme(
    primary = RoyalBlueBright,
    onPrimary = TextPrimary,
    primaryContainer = RoyalBlueMuted,
    onPrimaryContainer = TextPrimary,
    secondary = BullGreenBright,
    onSecondary = TextPrimary,
    tertiary = BearOrangeRed,
    onTertiary = TextPrimary,
    background = DeepSpaceBg,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    outlineVariant = DividerSubtle,
    error = BearRedSoft,
    onError = TextPrimary,
)

@Composable
fun TradeHubTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TradeHubDarkColorScheme,
        typography = TradeHubTypography,
        content = content,
    )
}
