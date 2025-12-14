package com.kakdela.p2p.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Ваша неоновая тёмная цветовая схема
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00FFF0),      // Неоновый голубой
    secondary = Color(0xFFFF00C8),     // Неоновый розовый
    tertiary = Color(0xFFD700FF),      // Неоновый фиолетовый
    background = Color(0xFF000000),    // Чёрный фон
    surface = Color(0xFF1A1A1A),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

// Дефолтные shapes и typography — прямо здесь, без конфликтов
private val AppShapes = Shapes()
private val AppTypography = Typography()

@Composable
fun Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
