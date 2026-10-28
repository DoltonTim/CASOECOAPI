package pe.upeu.ecorecicla.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EcoColorScheme = lightColorScheme(
    primary = Color(0xFF1B5E20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0A3D0E),
    secondary = Color(0xFF00695C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF003D33),
    tertiary = Color(0xFFE65100),
    onTertiary = Color.White,
    background = Color(0xFFF6F9F6),
    surface = Color.White,
    error = Color(0xFFB71C1C),
    onError = Color.White
)

@Composable
fun EcoReciclaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EcoColorScheme,
        content = content
    )
}
