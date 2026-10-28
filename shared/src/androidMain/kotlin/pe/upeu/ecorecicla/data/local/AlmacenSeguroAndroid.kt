package pe.upeu.ecorecicla.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme
import androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme
import androidx.security.crypto.MasterKey
import pe.upeu.ecorecicla.domain.model.Sesion

/**
 * ============================================================================
 * ALMACENAMIENTO SEGURO DE ANDROID CON KEYSTORE + ENCRYPTEDSHAREDPREFERENCES
 * (SECCION 4.4 Y 4.7)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Pregunta 3):
 * - ¿Donde se guarda el token en el dispositivo y por que ahi?:
 *   Se guarda en `EncryptedSharedPreferences` ("ecorecicla_sesion_segura") respaldado por
 *   el `AndroidKeyStore` mediante una `MasterKey` con esquema `AES256_GCM`.
 *   Las claves se cifran de forma determinista con `AES256_SIV` y los valores con `AES256_GCM`.
 * - ¿Que pasaria si lo guardaras en preferencias sin cifrar (`SharedPreferences` normal)?:
 *   El archivo XML en `/data/data/pe.upeu.ecorecicla/shared_prefs/` quedaria legible en texto
 *   plano. Como los telefonos de EcoRecicla son equipos compartidos entre turnos, cualquiera
 *   con depuracion ADB podria extraer el token JWT y ejecutar operaciones con privilegios ajenos.
 */
class AlmacenSeguroAndroid(contexto: Context) : AlmacenSeguro {

    private val llaveMaestra = MasterKey.Builder(contexto)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        contexto,
        "ecorecicla_sesion_segura",
        llaveMaestra,
        PrefKeyEncryptionScheme.AES256_SIV,
        PrefValueEncryptionScheme.AES256_GCM
    )

    override fun guardarToken(token: String) {
        prefs.edit().putString(CLAVE_TOKEN, token).apply()
    }

    override fun leerToken(): String? = prefs.getString(CLAVE_TOKEN, null)

    override fun borrarToken() {
        prefs.edit().remove(CLAVE_TOKEN).apply()
    }

    override fun guardarSesionCache(sesion: Sesion) {
        prefs.edit()
            .putString(CLAVE_TOKEN, sesion.token)
            .putString(CLAVE_USUARIO, sesion.usuario)
            .putString(CLAVE_NOMBRE, sesion.nombreCompleto)
            .putString(CLAVE_ROL, sesion.rol)
            .putString(CLAVE_PRIVILEGIOS, sesion.privilegios.joinToString(","))
            .apply()
    }

    override fun leerSesionCache(): Sesion? {
        val token = leerToken() ?: return null
        val usuario = prefs.getString(CLAVE_USUARIO, null) ?: return null
        val rol = prefs.getString(CLAVE_ROL, "OPERARIO") ?: "OPERARIO"
        val nombre = prefs.getString(CLAVE_NOMBRE, usuario) ?: usuario
        val privsRaw = prefs.getString(CLAVE_PRIVILEGIOS, "") ?: ""
        val privilegios = privsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return Sesion(
            token = token,
            usuario = usuario,
            rol = rol,
            privilegios = privilegios,
            nombreCompleto = nombre
        )
    }

    override fun borrarSesion() {
        prefs.edit()
            .remove(CLAVE_TOKEN)
            .remove(CLAVE_USUARIO)
            .remove(CLAVE_NOMBRE)
            .remove(CLAVE_ROL)
            .remove(CLAVE_PRIVILEGIOS)
            .apply()
    }

    private companion object {
        const val CLAVE_TOKEN = "jwt_token"
        const val CLAVE_USUARIO = "usuario_login"
        const val CLAVE_NOMBRE = "usuario_nombre"
        const val CLAVE_ROL = "usuario_rol"
        const val CLAVE_PRIVILEGIOS = "usuario_privilegios"
    }
}
