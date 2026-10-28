package pe.upeu.ecorecicla.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Sesion

/**
 * ============================================================================
 * DTOS DE LA CAPA DE DATOS Y FUNCIONES DE CONVERSION AL DOMINIO (SECCION 4.2 Y 4.4)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Pregunta 6):
 * - Los DTOs viven exclusivamente en `data/remote/dto/` y usan `@Serializable` y `@SerialName`.
 * - El modelo de dominio (`domain/model/`) NO conoce anotaciones de serializacion.
 * - Todos los listados del servicio se deserializan con [PaginaResponseDto] (nunca como lista suelta).
 */

@Serializable
data class PaginaResponseDto<T>(
    @SerialName("contenido") val contenido: List<T> = emptyList(),
    @SerialName("pagina") val pagina: Int = 0,
    @SerialName("tamanio") val tamanio: Int = 20,
    @SerialName("totalElementos") val totalElementos: Long = 0L,
    @SerialName("totalPaginas") val totalPaginas: Int = 0,
    @SerialName("ultima") val ultima: Boolean = true
)

@Serializable
data class ErrorResponseDto(
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("status") val status: Int = 500,
    @SerialName("error") val error: String? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("validationErrors") val validationErrors: Map<String, String>? = null
)

@Serializable
data class LoginRequestDto(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String
)

@Serializable
data class LoginResponseDto(
    @SerialName("token") val token: String,
    @SerialName("tipo") val tipo: String = "Bearer",
    @SerialName("expiraEnSegundos") val expiraEnSegundos: Long = 3600L,
    @SerialName("usuario") val usuario: String,
    @SerialName("nombreCompleto") val nombreCompleto: String = "",
    @SerialName("rol") val rol: String,
    @SerialName("privilegios") val privilegios: Set<String> = emptySet()
) {
    fun aDominio(): Sesion = Sesion(
        token = token,
        usuario = usuario,
        rol = rol,
        privilegios = privilegios,
        nombreCompleto = nombreCompleto.ifBlank { usuario }
    )
}

@Serializable
data class UsuarioActualDto(
    @SerialName("id") val id: Long = 0L,
    @SerialName("usuario") val usuario: String,
    @SerialName("nombreCompleto") val nombreCompleto: String = "",
    @SerialName("rol") val rol: String,
    @SerialName("privilegios") val privilegios: Set<String> = emptySet()
) {
    fun aDominio(token: String): Sesion = Sesion(
        token = token,
        usuario = usuario,
        rol = rol,
        privilegios = privilegios,
        nombreCompleto = nombreCompleto.ifBlank { usuario }
    )
}

@Serializable
data class PuntoAcopioDto(
    @SerialName("id") val id: Long,
    @SerialName("razonSocial") val razonSocial: String,
    @SerialName("direccion") val direccion: String,
    @SerialName("zona") val zona: String,
    @SerialName("materialHabitual") val materialHabitual: String,
    @SerialName("secuencia") val secuencia: Int,
    @SerialName("atendido") val atendido: Boolean = false
) {
    fun aDominio(): PuntoAcopio = PuntoAcopio(
        id = id,
        razonSocial = razonSocial,
        direccion = direccion,
        zona = zona,
        materialHabitual = materialHabitual,
        secuencia = secuencia,
        atendido = atendido
    )
}

@Serializable
data class RutaDiaDto(
    @SerialName("id") val id: Long,
    @SerialName("codigo") val codigo: String = "",
    @SerialName("fecha") val fecha: String,
    @SerialName("zona") val zona: String = "Multizona",
    @SerialName("estado") val estado: String,
    @SerialName("operario") val operario: String = "",
    @SerialName("puntos") val puntos: List<PuntoAcopioDto> = emptyList()
)

@Serializable
data class PesajeDto(
    @SerialName("id") val id: Long,
    @SerialName("puntoId") val puntoId: Long,
    @SerialName("fechaHora") val fechaHora: String,
    @SerialName("material") val material: String,
    @SerialName("pesoBruto") val pesoBruto: Double,
    @SerialName("tara") val tara: Double,
    @SerialName("pesoNeto") val pesoNeto: Double,
    @SerialName("humedadEstimada") val humedadEstimada: Double = 0.0,
    @SerialName("observacion") val observacion: String? = null,
    @SerialName("operario") val operario: String,
    @SerialName("estado") val estado: String = "REGISTRADO",
    @SerialName("motivoAnulacion") val motivoAnulacion: String? = null
) {
    fun aDominio(pendiente: Boolean = false): Pesaje = Pesaje(
        id = id,
        puntoId = puntoId,
        fechaHora = fechaHora,
        material = material,
        pesoBruto = pesoBruto,
        tara = tara,
        pesoNeto = pesoNeto,
        operario = operario,
        estado = if (estado.equals("ANULADO", ignoreCase = true)) {
            EstadoPesaje.Anulado(motivoAnulacion ?: "Anulado por supervisor")
        } else {
            EstadoPesaje.Registrado
        },
        pendiente = pendiente,
        observacion = observacion,
        humedadEstimada = humedadEstimada
    )
}

/**
 * El cliente NUNCA envia el pesoNeto: lo calcula el servicio como pesoBruto - tara (RN-03).
 */
@Serializable
data class PesajeRequestDto(
    @SerialName("puntoId") val puntoId: Long,
    @SerialName("material") val material: String,
    @SerialName("pesoBruto") val pesoBruto: Double,
    @SerialName("tara") val tara: Double,
    @SerialName("humedadEstimada") val humedadEstimada: Double = 0.0,
    @SerialName("observacion") val observacion: String? = null
)

@Serializable
data class AnularPesajeRequestDto(
    @SerialName("motivo") val motivo: String
)
