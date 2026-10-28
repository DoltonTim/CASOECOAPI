package pe.upeu.ecorecicla

import androidx.compose.runtime.Composable
import pe.upeu.ecorecicla.presentation.navigation.EcoNavigation
import pe.upeu.ecorecicla.presentation.theme.EcoReciclaTheme

@Composable
fun App() {
    EcoReciclaTheme {
        EcoNavigation()
    }
}
