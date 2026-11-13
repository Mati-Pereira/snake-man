package dev.matheus.snakeman

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.matheus.snakeman.ui.SnakeManApp
import dev.matheus.snakeman.ui.theme.SnakeManTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // O jogo é sempre escuro, então as barras do sistema usam ícones claros.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb())
        )
        setContent {
            SnakeManTheme {
                SnakeManApp()
            }
        }
    }
}
