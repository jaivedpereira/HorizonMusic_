package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HorizonColorScheme = darkColorScheme(
    primary = HorizonSunset,
    secondary = TwilightGlow,
    tertiary = HorizonGold,
    background = ObsidianBlack,
    surface = ObsidianSlate,
    surfaceContainer = ObsidianGray,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = TextWhite,
    onSurface = TextWhite,
    onSurfaceVariant = TextMuted,
    outline = DividerColor
)

@Composable
fun HorizonMusicTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HorizonColorScheme,
        typography = Typography,
        content = content
    )
}
