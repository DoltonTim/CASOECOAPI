package pe.upeu.ecorecicla.data.remote

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.remote.dto.ErrorResponseDto
import pe.upeu.ecorecicla.domain.model.ErrorApi

/**
 * ============================================================================
 * TRADUCCION CENTRALIZADA DE RESPUESTAS Y EXCEPCIONES A ERRORAPI (SECCION 3.5 Y 4.4)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Preguntas 5 y 11):
 * - Diferencia entre 401 y 403 en nuestra aplicacion:
 *   - `401 Unauthorized` ([ErrorApi.NoAutorizado]): El token no se envio, fue alterado o vencio.
 *     Aqui se borra inmediatamente el token de [AlmacenSeguro] (`almacenSeguro?.borrarSesion()`)
 *     y la aplicacion vuelve a la pantalla de inicio de sesion.
 *   - `403 Forbidden` ([ErrorApi.SinPrivilegio]): El token es valido, pero el usuario no tiene
 *     el privilegio requerido (o incumplio RN-02 al intentar editar el pesaje de otro operario).
 *     La sesion SE MANTIENE intacta y solo se informa en pantalla el motivo.
 * - Camino del error 400 de validacion (`validationErrors`):
 *   Se deserializa `ErrorResponseDto`, se extrae el mapa `validationErrors` y se entrega en
 *   `ErrorApi.Validacion(porCampo = ...)` para que el ViewModel lo ubique bajo el campo del formulario.
 */
private val jsonError = Json { ignoreUnknownKeys = true; isLenient = true }

suspend fun <T> ejecutarLlamadaRemota(
    almacenSeguro: AlmacenSeguro? = null,
    bloque: suspend () -> T
): Result<T> = try {
    Result.success(bloque())
} catch (cancelacion: CancellationException) {
    throw cancelacion
} catch (errorActual: ErrorApi) {
    Result.failure(errorActual)
} catch (e: ClientRequestException) {
    val traducido = traducirErrorCliente(e, almacenSeguro)
    Result.failure(traducido)
} catch (e: ServerResponseException) {
    val cuerpo = leerErrorDto(e)
    Result.failure(
        ErrorApi.Servidor(cuerpo?.message ?: "Fallo interno del servidor (${e.response.status.value})")
    )
} catch (e: HttpRequestTimeoutException) {
    Result.failure(ErrorApi.TiempoAgotado)
} catch (e: Exception) {
    Result.failure(ErrorApi.SinConexion)
}

suspend fun traducirErrorCliente(
    e: ClientRequestException,
    almacenSeguro: AlmacenSeguro? = null
): ErrorApi {
    val cuerpo = leerErrorDto(e)
    return when (e.response.status.value) {
        400 -> ErrorApi.Validacion(
            porCampo = cuerpo?.validationErrors.orEmpty(),
            detalle = cuerpo?.message ?: "Datos inválidos en el formulario"
        )
        401 -> {
            // Al recibir 401 (token vencido o alterado), se limpia el token del almacenamiento seguro
            almacenSeguro?.borrarSesion()
            ErrorApi.NoAutorizado(
                detalle = cuerpo?.message ?: "Su sesión ha expirado o las credenciales son incorrectas"
            )
        }
        403 -> ErrorApi.SinPrivilegio(
            detalle = cuerpo?.message ?: "No está autorizado para realizar esta operación"
        )
        404 -> ErrorApi.NoEncontrado(
            detalle = cuerpo?.message ?: "El recurso solicitado no existe en el servidor"
        )
        409 -> ErrorApi.Conflicto(
            motivo = cuerpo?.message ?: "Conflicto con una regla de negocio del servidor"
        )
        else -> ErrorApi.Servidor(
            detalle = cuerpo?.message ?: "Error inesperado (${e.response.status.value})"
        )
    }
}

private suspend fun leerErrorDto(e: io.ktor.client.plugins.ResponseException): ErrorResponseDto? =
    runCatching {
        e.response.body<ErrorResponseDto>()
    }.getOrElse {
        runCatching {
            val texto = e.response.bodyAsText()
            jsonError.decodeFromString<ErrorResponseDto>(texto)
        }.getOrNull()
    }
