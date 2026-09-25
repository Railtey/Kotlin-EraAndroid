package com.eraandroid.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF88AAFF),
    secondary = Color(0xFF88DDAA),
    tertiary = Color(0xFFFFAA88),
    background = Color(0xFF0A0A0F),
    surface = Color(0xFF111122),
    onPrimary = Color(0xFF000000),
    onSecondary = Color(0xFF000000),
    onBackground = Color(0xFFEEEEEE),
    onSurface = Color(0xFFCCCCCC),
    error = Color(0xFFFF6666)
)

@Composable
fun EraAndroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}