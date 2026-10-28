package pe.upeu.ecorecicla.domain.model

/**
 * ============================================================================
 * JERARQUIA SELLADA DE ERRORES DEL DOMINIO (SECCIONES 3.5 Y 4.4)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Preguntas 5 y 11):
 * La capa de datos (`MapeoErrores.kt`) traduce cada respuesta HTTP o fallo de red
 * a uno de estos 8 tipos del dominio, sin que la capa `presentation` conozca Ktor:
 * - [Validacion]    (400 Bad Request): contiene el mapa `porCampo` (`validationErrors`)
 *                   para mostrar el mensaje debajo del campo que lo origino.
 * - [NoAutorizado]  (401 Unauthorized): token ausente, alterado o vencido -> se borra
 *                   el token de `AlmacenSeguro`, se limpia la sesion y se vuelve al login.
 * - [SinPrivilegio] (403 Forbidden): autenticado pero sin el privilegio requerido (o RN-02) ->
 *                   se informa que la operacion no esta autorizada y la sesion SE MANTIENE.
 * - [NoEncontrado]  (404 Not Found): el recurso no existe -> se informa y se refresca el listado.
 * - [Conflicto]     (409 Conflict): regla de negocio del servidor incumplida (RN-01 o RN-04) ->
 *                   se muestra el motivo exacto devuelto por el servidor.
 * - [Servidor]      (500 Internal Server Error): fallo del servidor -> mensaje breve y reintentar.
 * - [SinConexion]   (IOException): sin cobertura / modo avion -> operacion local queda pendiente.
 * - [TiempoAgotado] (HttpRequestTimeoutException): tiempo de espera agotado.
 */
sealed class ErrorApi(override val message: String) : Exception(message) {

    data class Validacion(
        val porCampo: Map<String, String>,
        val detalle: String = "Existen errores de validación en el formulario"
    ) : ErrorApi(detalle)

    data class NoAutorizado(
        val detalle: String = "Sesión expirada o credenciales incorrectas"
    ) : ErrorApi(detalle)

    data class SinPrivilegio(
        val detalle: String = "Operación no autorizada para su nivel de privilegios"
    ) : ErrorApi(detalle)

    data class NoEncontrado(
        val detalle: String = "El recurso solicitado ya no existe"
    ) : ErrorApi(detalle)

    data class Conflicto(
        val motivo: String
    ) : ErrorApi(motivo)

    data class Servidor(
        val detalle: String = "Fallo interno del servidor. Intente nuevamente."
    ) : ErrorApi(detalle)

    data object SinConexion : ErrorApi("Sin conexión a la red. Trabajando en modo local (offline).")

    data object TiempoAgotado : ErrorApi("El servidor tardó demasiado en responder (tiempo agotado).")
}
