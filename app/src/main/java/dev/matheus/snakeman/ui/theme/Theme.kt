package dev.matheus.snakeman.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Night = Color(0xFF0B1220)
val BoardBg = Color(0xFF0F1B2A)
val BoardGrid = Color(0xFF17283D)
val SurfaceCard = Color(0xFF132133)

val SnakeGreen = Color(0xFF4CD964)
val SnakeHeadGreen = Color(0xFF7CE38B)
val TailGreen = Color(0xFF2E9E5B)
val Gold = Color(0xFFFFC93C)
val GoldDeep = Color(0xFFE09A12)
val AppleRed = Color(0xFFFF5A5F)
val TextPrimary = Color(0xFFE7F0FF)
val TextMuted = Color(0xFF8AA0BC)

private val ArcadeColors = darkColorScheme(
    primary = SnakeGreen,
    onPrimary = Color(0xFF05230F),
    secondary = AppleRed,
    onSecondary = Color(0xFF2B0508),
    background = Night,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = BoardBg,
    onSurfaceVariant = TextMuted,
    outline = Color(0xFF2B4260)
)

@Composable
fun SnakeManTheme(content: @Composable () -> Unit) {
    // O jogo é sempre escuro: é a identidade visual do arcade.
    MaterialTheme(colorScheme = ArcadeColors, content = content)
}
