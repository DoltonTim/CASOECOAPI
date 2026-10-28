package pe.upeu.ecorecicla.data.remote

/**
 * ============================================================================
 * CONFIGURACION CENTRALIZADA DEL SERVICIO REMOTO (SECCION 4.4 Y 4.7)
 * ============================================================================
 * - En el emulador de Android, `10.0.2.2` apunta al `localhost` de la PC anfitriona
 *   donde corre el backend propio `EcoAPI` en el puerto `8080`.
 * - Se definen los tiempos de espera (timeouts) de conexion, peticion y socket.
 */
object ApiConfig {
    const val URL_BASE = "http://10.0.2.2:8080/api/v1/"
    const val TIMEOUT_PETICION_MS = 15_000L
    const val TIMEOUT_CONEXION_MS = 10_000L
    const val TIMEOUT_SOCKET_MS = 15_000L
}
