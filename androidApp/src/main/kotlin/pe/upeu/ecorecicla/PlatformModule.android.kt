package pe.upeu.ecorecicla

import org.koin.core.module.Module
import pe.upeu.ecorecicla.di.platformModule as sharedPlatformModule

/**
 * Referencia directa en `androidApp/src/main/kotlin/pe/upeu/ecorecicla/PlatformModule.android.kt`
 * conforme al arbol de paquetes de la Seccion 4.7 (motor HTTP OkHttp + dependencias de Android).
 */
val androidPlatformModule: Module = sharedPlatformModule
