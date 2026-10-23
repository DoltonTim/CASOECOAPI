package pe.upeu.ecorecicla.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import pe.upeu.ecorecicla.data.remote.dto.AnularPesajeRequestDto
import pe.upeu.ecorecicla.data.remote.dto.LoginRequestDto
import pe.upeu.ecorecicla.data.remote.dto.LoginResponseDto
import pe.upeu.ecorecicla.data.remote.dto.PaginaResponseDto
import pe.upeu.ecorecicla.data.remote.dto.PesajeDto
import pe.upeu.ecorecicla.data.remote.dto.PesajeRequestDto
import pe.upeu.ecorecicla.data.remote.dto.PuntoAcopioDto
import pe.upeu.ecorecicla.data.remote.dto.RutaDiaDto
import pe.upeu.ecorecicla.data.remote.dto.UsuarioActualDto

/**
 * Cliente de operaciones REST contra EcoAPI (`/api/v1`).
 * Consume la unica instancia de [HttpClient] inyectada por Koin.
 */
open class EcoApiService(
    private val client: HttpClient
) {
    open suspend fun login(request: LoginRequestDto): LoginResponseDto =
        client.post("auth/login") {
            setBody(request)
        }.body()

    open suspend fun usuarioActual(): UsuarioActualDto =
        client.get("auth/me").body()

    open suspend fun obtenerRutaHoy(): RutaDiaDto =
        client.get("rutas/hoy").body()

    open suspend fun cerrarRutaHoy(): RutaDiaDto =
        client.post("rutas/hoy/cerrar").body()

    open suspend fun listarPuntos(
        zona: String? = null,
        pagina: Int = 0,
        tamanio: Int = 20
    ): PaginaResponseDto<PuntoAcopioDto> =
        client.get("puntos") {
            if (!zona.isNullOrBlank()) parameter("zona", zona)
            parameter("pagina", pagina)
            parameter("tamanio", tamanio)
        }.body()

    open suspend fun listarPesajesDePunto(
        puntoId: Long,
        pagina: Int = 0,
        tamanio: Int = 50
    ): PaginaResponseDto<PesajeDto> =
        client.get("puntos/$puntoId/pesajes") {
            parameter("pagina", pagina)
            parameter("tamanio", tamanio)
        }.body()

    open suspend fun registrarPesaje(request: PesajeRequestDto): PesajeDto =
        client.post("pesajes") {
            setBody(request)
        }.body()

    open suspend fun corregirPesaje(id: Long, request: PesajeRequestDto): PesajeDto =
        client.put("pesajes/$id") {
            setBody(request)
        }.body()

    open suspend fun anularPesaje(id: Long, motivo: String): PesajeDto =
        client.patch("pesajes/$id/anular") {
            setBody(AnularPesajeRequestDto(motivo = motivo))
        }.body()

    /** Soporte de 204 No Content sin intentar convertir el cuerpo (Seccion 3.5) */
    open suspend fun eliminarPesaje204(id: Long, motivo: String) {
        client.delete("pesajes/$id") {
            parameter("motivo", motivo)
        }
    }
}
