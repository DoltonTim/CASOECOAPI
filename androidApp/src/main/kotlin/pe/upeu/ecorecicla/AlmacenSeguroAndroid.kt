package pe.upeu.ecorecicla

import pe.upeu.ecorecicla.data.local.AlmacenSeguroAndroid as SharedAlmacenSeguroAndroid

/**
 * Referencia directa en `androidApp/src/main/kotlin/pe/upeu/ecorecicla/AlmacenSeguroAndroid.kt`
 * conforme al arbol de paquetes de la Seccion 4.7.
 * La implementacion con `EncryptedSharedPreferences` + `MasterKey` (`AES256_GCM`) vive en
 * `shared/src/androidMain/kotlin/pe/upeu/ecorecicla/data/local/AlmacenSeguroAndroid.kt`.
 */
typealias AlmacenSeguroAndroid = SharedAlmacenSeguroAndroid
