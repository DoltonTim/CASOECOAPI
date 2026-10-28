package pe.upeu.ecorecicla.web.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page

/**
 * ============================================================================
 * CONTRATO COMUN DE PAGINACION, ERRORES Y DTOS REST (SECCIONES 4.2 Y 4.3)
 * ============================================================================
 */

/**
 * Envoltorio de paginacion exigido en la Seccion 4.2:
 * { "contenido": [...], "pagina": 0, "tamanio": 20, "totalElementos": 37, "totalPaginas": 2, "ultima": false }
 */
data class PaginaResponseDto<T>(
    val contenido: List<T>,
    val pagina: Int,
    val tamanio: Int,
    val totalElementos: Long,
    val totalPaginas: Int,
    val ultima: Boolean
) {
    companion object {
        fun <T : Any> de(page: Page<T>): PaginaResponseDto<T> = PaginaResponseDto(
            contenido = page.content,
            pagina = page.number,
            tamanio = page.size,
            totalElementos = page.totalElements,
            totalPaginas = page.totalPages,
            ultima = page.isLast
        )
    }
}

/**
 * Formato de error comun exigido en la Seccion 4.2:
 * { "timestamp": "...", "status": 400, "error": "Bad Request", "message": "...", "path": "/api/v1/...", "validationErrors": { ... } }
 */
data class ErrorResponseDto(
    val timestamp: String,
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val validationErrors: Map<String, String>? = null
)

// ==================== AUTENTICACION (RF-B01 y RF-B07) ====================

data class LoginRequestDto(
    @field:NotBlank(message = "El usuario es obligatorio")
    val username: String = "",

    @field:NotBlank(message = "La contraseña es obligatoria")
    val password: String = ""
)

data class LoginResponseDto(
    val token: String,
    val tipo: String = "Bearer",
    val expiraEnSegundos: Long,
    val usuario: String,
    val nombreCompleto: String,
    val rol: String,
    val privilegios: Set<String>
)

data class UsuarioActualDto(
    val id: Long,
    val usuario: String,
    val nombreCompleto: String,
    val rol: String,
    val privilegios: Set<String>
)

// ==================== PUNTOS DE ACOPIO Y RUTA DEL DIA (RF-B02, RF-B03, RF-B06) ====================

data class PuntoAcopioDto(
    val id: Long,
    val razonSocial: String,
    val direccion: String,
    val zona: String,
    val materialHabitual: String,
    val secuencia: Int,
    val atendido: Boolean
)

data class PuntoAcopioRequestDto(
    @field:NotBlank(message = "La razón social es obligatoria")
    @field:Size(min = 3, max = 150, message = "La razón social debe tener entre 3 y 150 caracteres")
    val razonSocial: String = "",

    @field:NotBlank(message = "La dirección es obligatoria")
    val direccion: String = "",

    @field:NotBlank(message = "La zona es obligatoria")
    val zona: String = "",

    @field:NotBlank(message = "El material habitual es obligatorio")
    val materialHabitual: String = "",

    @field:Positive(message = "La secuencia debe ser mayor que cero")
    val secuencia: Int = 1
)

data class RutaDiaDto(
    val id: Long,
    val codigo: String,
    val fecha: String,
    val zona: String,
    val estado: String,
    val operario: String,
    val puntos: List<PuntoAcopioDto>
)

// ==================== PESAJES (RF-B04 y RF-B05 + SC-C humedadEstimada) ====================

data class PesajeDto(
    val id: Long,
    val puntoId: Long,
    val fechaHora: String,
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val pesoNeto: Double,
    val humedadEstimada: Double = 0.0,
    val observacion: String? = null,
    val operario: String,
    val estado: String,
    val motivoAnulacion: String? = null
)

/**
 * El cliente NUNCA envia el pesoNeto: lo calcula el servicio como pesoBruto - tara (RN-03).
 */
data class PesajeRequestDto(
    @field:NotNull(message = "El punto de acopio es obligatorio")
    @field:Positive(message = "El identificador del punto debe ser positivo")
    val puntoId: Long? = null,

    @field:NotBlank(message = "El tipo de material es obligatorio")
    @field:Size(min = 2, max = 80, message = "El material debe tener entre 2 y 80 caracteres")
    val material: String = "",

    @field:NotNull(message = "El peso bruto es obligatorio")
    @field:DecimalMin(value = "0.1", message = "El peso bruto debe ser mayor que cero")
    val pesoBruto: Double? = null,

    @field:NotNull(message = "La tara del contenedor es obligatoria")
    @field:DecimalMin(value = "0.0", message = "La tara no puede ser negativa")
    val tara: Double? = null,

    @field:DecimalMin(value = "0.0", message = "La humedad estimada debe estar entre 0 y 100%")
    @field:DecimalMax(value = "100.0", message = "La humedad estimada no puede superar el 100%")
    val humedadEstimada: Double = 0.0,

    @field:Size(max = 250, message = "La observación no puede superar 250 caracteres")
    val observacion: String? = null
)

data class AnularPesajeRequestDto(
    @field:NotBlank(message = "El motivo de anulación es obligatorio")
    @field:Size(min = 4, max = 250, message = "El motivo de anulación debe tener al menos 4 caracteres")
    val motivo: String = ""
)


/**
 * Solicitud de cambio SC-D (Parte II - Rama DOLTON-MEZA):
 * DTO para GET /api/v1/rutas/hoy/resumen con indicadores de avance y totales de la ruta.
 */
data class ResumenRutaDto(
    val fecha: String,
    val totalPuntos: Int,
    val puntosAtendidos: Int,
    val totalPesajes: Int,
    val totalKilosNetos: Double
)
