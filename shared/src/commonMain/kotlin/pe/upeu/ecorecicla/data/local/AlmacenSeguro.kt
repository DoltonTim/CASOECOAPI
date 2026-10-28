package pe.upeu.ecorecicla.data.local

import pe.upeu.ecorecicla.domain.model.Sesion

/**
 * ============================================================================
 * CONTRATO DEL ALMACENAMIENTO SEGURO DE SESION Y TOKEN (SECCION 4.4 Y 4.7)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Pregunta 3):
 * - ¿Donde se guarda el token en el dispositivo y por que ahi?:
 *   En Android se implementa en `AlmacenSeguroAndroid` usando `EncryptedSharedPreferences`
 *   respaldado por el `AndroidKeyStore` (`MasterKey` con `AES256_GCM` para valores y
 *   `AES256_SIV` para claves).
 * - ¿Que pasaria si lo guardaras en preferencias sin cifrar (`SharedPreferences` normal)?:
 *   El archivo XML en `/data/data/pe.upeu.ecorecicla/shared_prefs/` quedaria en texto claro.
 *   En un dispositivo compartido entre turnos, con un backup ADB o acceso root, otro usuario
 *   podria extraer el JWT y suplantar al supervisor o al operario.
 */
interface AlmacenSeguro {
    fun guardarToken(token: String)
    fun leerToken(): String?
    fun borrarToken()

    fun guardarSesionCache(sesion: Sesion)
    fun leerSesionCache(): Sesion?
    fun borrarSesion() {
        borrarToken()
    }
}
