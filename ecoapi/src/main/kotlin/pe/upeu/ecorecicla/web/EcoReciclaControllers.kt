package pe.upeu.ecorecicla.web

import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import pe.upeu.ecorecicla.servicio.EcoReciclaService
import pe.upeu.ecorecicla.web.dto.AnularPesajeRequestDto
import pe.upeu.ecorecicla.web.dto.PaginaResponseDto
import pe.upeu.ecorecicla.web.dto.PesajeDto
import pe.upeu.ecorecicla.web.dto.PesajeRequestDto
import pe.upeu.ecorecicla.web.dto.PuntoAcopioDto
import pe.upeu.ecorecicla.web.dto.PuntoAcopioRequestDto
import pe.upeu.ecorecicla.web.dto.ResumenRutaDto
import pe.upeu.ecorecicla.web.dto.RutaDiaDto
import java.time.LocalDate

/**
 * ============================================================================
 * CONTROLADORES REST VERSIONADOS BAJO /api/v1 (SECCION 4.3)
 * ============================================================================
 * Ningun controlador verifica roles con `if`: la autorizacion ocurre en
 * `SecurityConfig` contra los privilegios del token y las reglas RN-01..RN-04
 * se coordinan en `EcoReciclaService`.
 */

@RestController
@RequestMapping("/api/v1/rutas")
class RutaController(
    private val ecoService: EcoReciclaService
) {

    /** RF-B02: Ruta del dia asignada al usuario autenticado, con sus puntos en orden de visita */
    @GetMapping("/hoy")
    fun obtenerRutaHoy(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fecha: LocalDate?
    ): ResponseEntity<RutaDiaDto> =
        ResponseEntity.ok(ecoService.obtenerRutaDelDia(fecha))

    /** Solicitud de cambio SC-D (Parte II - Rama DOLTON-MEZA): Resumen de la ruta del dia */
    @GetMapping("/hoy/resumen")
    fun obtenerResumenRutaHoy(): ResponseEntity<ResumenRutaDto> =
        ResponseEntity.ok(ecoService.obtenerResumenRutaDelDia())

    /** RF-B06: Cierre de la ruta del dia por ID (solo privilegio RUTA_CERRAR, aplica RN-04) */
    @PostMapping("/{id}/cerrar")
    fun cerrarRutaPorId(@PathVariable id: Long): ResponseEntity<RutaDiaDto> =
        ResponseEntity.ok(ecoService.cerrarRuta(id))

    /** RF-B06: Cierre de la ruta activa de hoy (solo privilegio RUTA_CERRAR, aplica RN-04) */
    @PostMapping("/hoy/cerrar")
    fun cerrarRutaHoy(): ResponseEntity<RutaDiaDto> =
        ResponseEntity.ok(ecoService.cerrarRuta(null))
}

@RestController
@RequestMapping("/api/v1/puntos")
class PuntoAcopioController(
    private val ecoService: EcoReciclaService
) {

    /** RF-B03: Listado paginado de puntos de acopio con filtro opcional por zona */
    @GetMapping
    fun listarPuntos(
        @RequestParam(required = false) zona: String?,
        @RequestParam(defaultValue = "0") pagina: Int,
        @RequestParam(defaultValue = "20") tamanio: Int
    ): ResponseEntity<PaginaResponseDto<PuntoAcopioDto>> =
        ResponseEntity.ok(ecoService.listarPuntosAcopio(zona, pagina, tamanio))

    @GetMapping("/{id}")
    fun obtenerPunto(@PathVariable id: Long): ResponseEntity<PuntoAcopioDto> =
        ResponseEntity.ok(ecoService.obtenerPunto(id))

    @PostMapping
    fun crearPunto(@Valid @RequestBody request: PuntoAcopioRequestDto): ResponseEntity<PuntoAcopioDto> =
        ResponseEntity.status(HttpStatus.CREATED).body(ecoService.crearPuntoAcopio(request))

    /** Seccion 4.3: Pesajes de un punto paginados del mas reciente al mas antiguo (PESAJE_LEER) */
    @GetMapping("/{puntoId}/pesajes")
    fun listarPesajesDePunto(
        @PathVariable puntoId: Long,
        @RequestParam(defaultValue = "0") pagina: Int,
        @RequestParam(defaultValue = "20") tamanio: Int
    ): ResponseEntity<PaginaResponseDto<PesajeDto>> =
        ResponseEntity.ok(ecoService.listarPesajesDePunto(puntoId, pagina, tamanio))

    /** RF-B04 alternativo anidado: Registrar pesaje sobre un punto de la ruta */
    @PostMapping("/{puntoId}/pesajes")
    fun registrarPesajeEnPunto(
        @PathVariable puntoId: Long,
        @Valid @RequestBody request: PesajeRequestDto,
        authentication: Authentication
    ): ResponseEntity<PesajeDto> {
        val conPunto = request.copy(puntoId = puntoId)
        val creado = ecoService.registrarPesaje(conPunto, authentication.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(creado)
    }
}

@RestController
@RequestMapping("/api/v1/pesajes")
class PesajeController(
    private val ecoService: EcoReciclaService
) {

    /** Consulta paginada de pesajes por puntoId (del mas reciente al mas antiguo) */
    @GetMapping
    fun listarPorPunto(
        @RequestParam puntoId: Long,
        @RequestParam(defaultValue = "0") pagina: Int,
        @RequestParam(defaultValue = "20") tamanio: Int
    ): ResponseEntity<PaginaResponseDto<PesajeDto>> =
        ResponseEntity.ok(ecoService.listarPesajesDePunto(puntoId, pagina, tamanio))

    /** RF-B04: Registro de pesaje nuevo -> 201 Created con id definitivo y pesoNeto calculado */
    @PostMapping
    fun registrarPesaje(
        @Valid @RequestBody request: PesajeRequestDto,
        authentication: Authentication
    ): ResponseEntity<PesajeDto> {
        val creado = ecoService.registrarPesaje(request, authentication.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(creado)
    }

    /** RF-B05: Correccion de un pesaje existente -> 200 OK (aplica RN-01, RN-02 y RN-03) */
    @PutMapping("/{id}")
    fun corregirPesaje(
        @PathVariable id: Long,
        @Valid @RequestBody request: PesajeRequestDto,
        authentication: Authentication
    ): ResponseEntity<PesajeDto> {
        val privilegios = authentication.authorities.mapNotNull { it.authority }.toSet()
        val actualizado = ecoService.corregirPesaje(id, request, authentication.name, privilegios)
        return ResponseEntity.ok(actualizado)
    }

    /** RF-B05: Anulacion de un pesaje -> 200 OK marcandolo como ANULADO sin borrarlo de la base */
    @PatchMapping("/{id}/anular")
    fun anularPesajePatch(
        @PathVariable id: Long,
        @Valid @RequestBody request: AnularPesajeRequestDto
    ): ResponseEntity<PesajeDto> =
        ResponseEntity.ok(ecoService.anularPesaje(id, request))

    @PostMapping("/{id}/anular")
    fun anularPesajePost(
        @PathVariable id: Long,
        @Valid @RequestBody request: AnularPesajeRequestDto
    ): ResponseEntity<PesajeDto> =
        ResponseEntity.ok(ecoService.anularPesaje(id, request))

    /** Soporta tambien DELETE devolviendo 204 No Content pero marcando como ANULADO en la base */
    @DeleteMapping("/{id}")
    fun anularPesajeDelete(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "Anulado por supervisor") motivo: String
    ): ResponseEntity<Void> {
        ecoService.anularPesaje(id, AnularPesajeRequestDto(motivo = motivo))
        return ResponseEntity.noContent().build()
    }
}

@RestController
@RequestMapping("/api/v1/health")
class HealthController {
    @GetMapping
    fun health(): Map<String, String> = mapOf("status" to "UP", "servicio" to "EcoAPI")
}
