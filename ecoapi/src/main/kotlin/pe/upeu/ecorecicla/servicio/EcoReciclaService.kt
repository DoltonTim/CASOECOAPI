package pe.upeu.ecorecicla.servicio

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pe.upeu.ecorecicla.dominio.Pesaje
import pe.upeu.ecorecicla.dominio.PuntoAcopio
import pe.upeu.ecorecicla.dominio.RecursoNoEncontradoException
import pe.upeu.ecorecicla.dominio.ReglaNegocioException
import pe.upeu.ecorecicla.dominio.RutaDia
import pe.upeu.ecorecicla.dominio.RutaPunto
import pe.upeu.ecorecicla.dominio.SinPrivilegioException
import pe.upeu.ecorecicla.dominio.ValidacionCampoException
import pe.upeu.ecorecicla.repositorio.PesajeRepository
import pe.upeu.ecorecicla.repositorio.PuntoAcopioRepository
import pe.upeu.ecorecicla.repositorio.RutaDiaRepository
import pe.upeu.ecorecicla.repositorio.RutaPuntoRepository
import pe.upeu.ecorecicla.seguridad.Privilegio
import pe.upeu.ecorecicla.web.dto.AnularPesajeRequestDto
import pe.upeu.ecorecicla.web.dto.PaginaResponseDto
import pe.upeu.ecorecicla.web.dto.PesajeDto
import pe.upeu.ecorecicla.web.dto.PesajeRequestDto
import pe.upeu.ecorecicla.web.dto.PuntoAcopioDto
import pe.upeu.ecorecicla.web.dto.PuntoAcopioRequestDto
import pe.upeu.ecorecicla.web.dto.ResumenRutaDto
import pe.upeu.ecorecicla.web.dto.RutaDiaDto
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * ============================================================================
 * SERVICIO DE NEGOCIO Y TRANSACCIONES DE ECORECICLA (SECCIONES 3.4 Y 4.7)
 * ============================================================================
 * EXPLICACION PARA DEFENSA:
 * Aqui viven y se hacen cumplir las 4 reglas de negocio obligatorias (Seccion 3.4):
 * - RN-01: No se puede registrar ni corregir ningun pesaje en una ruta ya cerrada.
 *          El servicio responde 409 Conflict con el motivo.
 * - RN-02: Un operario solo puede corregir los pesajes que el mismo registro y
 *          unicamente dentro del mismo dia. Fuera de ese plazo, o si el pesaje es
 *          de otro operario, el servicio responde 403 Forbidden.
 * - RN-03: El peso neto lo calcula el servicio como la diferencia entre el bruto y
 *          la tara (pesoBruto - tara), y debe ser mayor que cero. Si no lo es,
 *          responde 400 Bad Request con el detalle en el campo `tara`.
 * - RN-04: La ruta no puede cerrarse mientras algun punto planificado no tenga un
 *          pesaje registrado (activo) o un motivo de omision. El servicio responde
 *          409 Conflict indicando cuantos puntos faltan.
 */
@Service
class EcoReciclaService(
    private val puntoAcopioRepository: PuntoAcopioRepository,
    private val rutaDiaRepository: RutaDiaRepository,
    private val rutaPuntoRepository: RutaPuntoRepository,
    private val pesajeRepository: PesajeRepository
) {

    @Transactional(readOnly = true)
    fun obtenerRutaDelDia(fechaOpcional: LocalDate? = null): RutaDiaDto {
        val ruta = if (fechaOpcional != null) {
            rutaDiaRepository.findFirstByFechaOrderByIdAsc(fechaOpcional)
                .orElseThrow { RecursoNoEncontradoException("No existe ruta programada para la fecha $fechaOpcional") }
        } else {
            rutaDiaRepository.findFirstByFechaOrderByIdAsc(LocalDate.now())
                .orElseGet {
                    rutaDiaRepository.findFirstByEstadoOrderByFechaDescIdAsc("ABIERTA")
                        .orElseThrow { RecursoNoEncontradoException("No existe ruta activa del día") }
                }
        }
        return mapearRuta(ruta)
    }

    /**
     * Solicitud de cambio SC-D (Parte II - Rama DOLTON-MEZA):
     * Calcula el resumen consolidado de la ruta del dia (`GET /api/v1/rutas/hoy/resumen`).
     */
    @Transactional(readOnly = true)
    fun obtenerResumenRutaDelDia(): ResumenRutaDto {
        val ruta = obtenerRutaDelDia(null)
        val todosLosPesajesActivos = ruta.puntos.flatMap { punto ->
            pesajeRepository.findByPuntoIdOrderByFechaHoraDescIdDesc(punto.id)
                .filter { it.estado.equals("REGISTRADO", ignoreCase = true) }
        }
        val totalKilosNetos = todosLosPesajesActivos
            .fold(BigDecimal.ZERO) { acc, p -> acc.add(p.pesoNeto) }
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()

        return ResumenRutaDto(
            fecha = ruta.fecha,
            totalPuntos = ruta.puntos.size,
            puntosAtendidos = ruta.puntos.count { it.atendido },
            totalPesajes = todosLosPesajesActivos.size,
            totalKilosNetos = totalKilosNetos
        )
    }

    @Transactional(readOnly = true)
    fun listarPuntosAcopio(zona: String?, pagina: Int, tamanio: Int): PaginaResponseDto<PuntoAcopioDto> {
        val pageable = PageRequest.of(pagina.coerceAtLeast(0), tamanio.coerceIn(1, 100))
        val page = puntoAcopioRepository.buscarPorZona(zona?.trim(), pageable)
        val dtos = page.map { punto -> aPuntoDto(punto, punto.secuencia) }
        return PaginaResponseDto.de(dtos)
    }

    @Transactional(readOnly = true)
    fun obtenerPunto(id: Long): PuntoAcopioDto {
        val punto = puntoAcopioRepository.findById(id)
            .orElseThrow { RecursoNoEncontradoException("Punto de acopio con id $id no encontrado") }
        return aPuntoDto(punto, punto.secuencia)
    }

    @Transactional
    fun crearPuntoAcopio(request: PuntoAcopioRequestDto): PuntoAcopioDto {
        val guardado = puntoAcopioRepository.save(
            PuntoAcopio(
                razonSocial = request.razonSocial.trim(),
                direccion = request.direccion.trim(),
                zona = request.zona.trim(),
                materialHabitual = request.materialHabitual.trim(),
                secuencia = request.secuencia,
                activo = true
            )
        )
        rutaDiaRepository.findFirstByEstadoOrderByFechaDescIdAsc("ABIERTA").ifPresent { rutaAbierta ->
            rutaPuntoRepository.save(
                RutaPunto(
                    ruta = rutaAbierta,
                    punto = guardado,
                    secuencia = request.secuencia,
                    motivoOmision = null
                )
            )
        }
        return aPuntoDto(guardado, guardado.secuencia)
    }

    @Transactional(readOnly = true)
    fun listarPesajesDePunto(puntoId: Long, pagina: Int, tamanio: Int): PaginaResponseDto<PesajeDto> {
        if (!puntoAcopioRepository.existsById(puntoId)) {
            throw RecursoNoEncontradoException("Punto de acopio con id $puntoId no encontrado")
        }
        val pageable = PageRequest.of(pagina.coerceAtLeast(0), tamanio.coerceIn(1, 100))
        val page = pesajeRepository.findByPuntoIdOrderByFechaHoraDescIdDesc(puntoId, pageable)
        return PaginaResponseDto.de(page.map { it.aDto() })
    }

    /**
     * RF-B04: Registro de pesaje nuevo sobre un punto de la ruta.
     * Verifica RN-01 (ruta no cerrada) y RN-03 (pesoNeto = pesoBruto - tara > 0).
     */
    @Transactional
    fun registrarPesaje(request: PesajeRequestDto, usuarioAutenticado: String): PesajeDto {
        val puntoId = request.puntoId
            ?: throw ValidacionCampoException("puntoId", "El punto de acopio es obligatorio")

        val punto = puntoAcopioRepository.findById(puntoId)
            .orElseThrow { RecursoNoEncontradoException("Punto de acopio con id $puntoId no encontrado") }

        val ruta = resolverRutaDePunto(puntoId)

        // RN-01: No se puede registrar ni corregir ningun pesaje en una ruta ya cerrada (409 Conflict)
        if (ruta.estado.equals("CERRADA", ignoreCase = true)) {
            throw ReglaNegocioException(
                "RN-01: No se puede registrar pesajes porque la ruta ${ruta.codigo} ya está cerrada"
            )
        }

        // RN-03: El peso neto lo calcula el servicio como pesoBruto - tara y debe ser > 0 (400 Bad Request)
        val bruto = BigDecimal.valueOf(request.pesoBruto ?: 0.0).setScale(2, RoundingMode.HALF_UP)
        val tara = BigDecimal.valueOf(request.tara ?: 0.0).setScale(2, RoundingMode.HALF_UP)
        val neto = calcularYValidarPesoNeto(bruto, tara)
        val humedad = BigDecimal.valueOf(request.humedadEstimada).setScale(2, RoundingMode.HALF_UP)

        val pesaje = Pesaje(
            punto = punto,
            ruta = ruta,
            fechaHora = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
            material = request.material.trim(),
            pesoBruto = bruto,
            tara = tara,
            pesoNeto = neto,
            humedadEstimada = humedad,
            observacion = request.observacion?.trim(),
            operario = usuarioAutenticado,
            estado = "REGISTRADO",
            motivoAnulacion = null
        )

        return pesajeRepository.save(pesaje).aDto()
    }

    /**
     * RF-B05: Correccion de un pesaje existente.
     * Verifica RN-01 (ruta abierta), RN-02 (mismo operario y mismo dia salvo privilegio de supervision)
     * y RN-03 (pesoNeto > 0).
     */
    @Transactional
    fun corregirPesaje(
        id: Long,
        request: PesajeRequestDto,
        usuarioAutenticado: String,
        privilegiosUsuario: Set<String>
    ): PesajeDto {
        val pesaje = pesajeRepository.findById(id)
            .orElseThrow { RecursoNoEncontradoException("Pesaje con id $id no encontrado") }

        // RN-01: No se puede registrar ni corregir ningun pesaje en una ruta ya cerrada (409 Conflict)
        if (pesaje.ruta.estado.equals("CERRADA", ignoreCase = true)) {
            throw ReglaNegocioException(
                "RN-01: No se puede corregir el pesaje #$id porque la ruta ${pesaje.ruta.codigo} ya está cerrada"
            )
        }

        if (pesaje.estado.equals("ANULADO", ignoreCase = true)) {
            throw ReglaNegocioException("No se puede corregir un pesaje que ya fue anulado")
        }

        // RN-02: Un operario (quien no posee el privilegio de anulacion/supervision global)
        // solo puede corregir los pesajes que el mismo registro y unicamente dentro del mismo dia (403 Forbidden).
        // Observa que verificamos contra el PRIVILEGIO, nunca contra el nombre del rol.
        val puedeEditarCualquiera = privilegiosUsuario.contains(Privilegio.PESAJE_ANULAR.name)
        if (!puedeEditarCualquiera) {
            val esPropio = pesaje.operario.equals(usuarioAutenticado, ignoreCase = true)
            val esDelMismoDia = pesaje.fechaHora.toLocalDate() == LocalDate.now()
            if (!esPropio || !esDelMismoDia) {
                throw SinPrivilegioException(
                    "RN-02: Un operario solo puede corregir los pesajes que él mismo registró y dentro del mismo día"
                )
            }
        }

        // RN-03: El peso neto lo calcula el servicio como pesoBruto - tara y debe ser > 0 (400 Bad Request)
        val bruto = BigDecimal.valueOf(request.pesoBruto ?: 0.0).setScale(2, RoundingMode.HALF_UP)
        val tara = BigDecimal.valueOf(request.tara ?: 0.0).setScale(2, RoundingMode.HALF_UP)
        val neto = calcularYValidarPesoNeto(bruto, tara)
        val humedad = BigDecimal.valueOf(request.humedadEstimada).setScale(2, RoundingMode.HALF_UP)

        pesaje.material = request.material.trim()
        pesaje.pesoBruto = bruto
        pesaje.tara = tara
        pesaje.pesoNeto = neto
        pesaje.humedadEstimada = humedad
        pesaje.observacion = request.observacion?.trim()

        return pesajeRepository.save(pesaje).aDto()
    }

    /**
     * RF-B05: Anulacion de un pesaje.
     * La anulacion NO borra el registro de la base de datos: lo marca como ANULADO con su motivo.
     */
    @Transactional
    fun anularPesaje(id: Long, request: AnularPesajeRequestDto): PesajeDto {
        val pesaje = pesajeRepository.findById(id)
            .orElseThrow { RecursoNoEncontradoException("Pesaje con id $id no encontrado") }

        if (pesaje.ruta.estado.equals("CERRADA", ignoreCase = true)) {
            throw ReglaNegocioException(
                "RN-01: No se puede anular el pesaje #$id porque la ruta ${pesaje.ruta.codigo} ya está cerrada"
            )
        }

        pesaje.estado = "ANULADO"
        pesaje.motivoAnulacion = request.motivo.trim()

        return pesajeRepository.save(pesaje).aDto()
    }

    /**
     * RF-B06: Cierre de ruta del dia.
     * Solo quien posee el privilegio RUTA_CERRAR puede invocarlo y debe cumplirse RN-04.
     */
    @Transactional
    fun cerrarRuta(rutaIdOpcional: Long? = null): RutaDiaDto {
        val ruta = if (rutaIdOpcional != null) {
            rutaDiaRepository.findById(rutaIdOpcional)
                .orElseThrow { RecursoNoEncontradoException("Ruta con id $rutaIdOpcional no encontrada") }
        } else {
            rutaDiaRepository.findFirstByFechaOrderByIdAsc(LocalDate.now())
                .orElseGet {
                    rutaDiaRepository.findFirstByEstadoOrderByFechaDescIdAsc("ABIERTA")
                        .orElseThrow { RecursoNoEncontradoException("No existe ruta abierta para cerrar") }
                }
        }

        if (ruta.estado.equals("CERRADA", ignoreCase = true)) {
            throw ReglaNegocioException("La ruta ${ruta.codigo} ya se encuentra cerrada")
        }

        val puntosRuta = rutaPuntoRepository.findByRutaIdOrderBySecuenciaAsc(ruta.id ?: 0L)
        val puntosPendientes = puntosRuta.count { rp ->
            val tienePesajeActivo = pesajeRepository.existsByPuntoIdAndEstado(rp.punto.id ?: 0L, "REGISTRADO")
            val tieneMotivoOmision = !rp.motivoOmision.isNullOrBlank()
            !tienePesajeActivo && !tieneMotivoOmision
        }

        // RN-04: La ruta no puede cerrarse mientras algun punto planificado no tenga un pesaje registrado
        // o un motivo de omision. El servicio responde 409 indicando cuantos puntos faltan.
        if (puntosPendientes > 0) {
            throw ReglaNegocioException(
                "RN-04: No se puede cerrar la ruta porque faltan atender $puntosPendientes punto(s) planificado(s)"
            )
        }

        ruta.estado = "CERRADA"
        val guardada = rutaDiaRepository.save(ruta)
        return mapearRuta(guardada)
    }

    private fun calcularYValidarPesoNeto(bruto: BigDecimal, tara: BigDecimal): BigDecimal {
        val neto = bruto.subtract(tara).setScale(2, RoundingMode.HALF_UP)
        if (neto <= BigDecimal.ZERO) {
            throw ValidacionCampoException(
                campo = "tara",
                mensaje = "RN-03: El peso neto (bruto - tara = $neto kg) debe ser mayor que cero. Verifique la tara."
            )
        }
        return neto
    }

    private fun resolverRutaDePunto(puntoId: Long): RutaDia {
        val asignacion = rutaPuntoRepository.findFirstByPuntoIdOrderByRutaIdAsc(puntoId)
        if (asignacion.isPresent) {
            return asignacion.get().ruta
        }
        return rutaDiaRepository.findFirstByFechaOrderByIdAsc(LocalDate.now())
            .orElseGet {
                rutaDiaRepository.findFirstByEstadoOrderByFechaDescIdAsc("ABIERTA")
                    .orElseThrow { RecursoNoEncontradoException("No se encontró ruta asignada para el punto $puntoId") }
            }
    }

    private fun mapearRuta(ruta: RutaDia): RutaDiaDto {
        val rutaId = ruta.id ?: 0L
        val asignaciones = rutaPuntoRepository.findByRutaIdOrderBySecuenciaAsc(rutaId)
        val puntos = asignaciones.map { rp ->
            val puntoId = rp.punto.id ?: 0L
            val atendido = pesajeRepository.existsByPuntoIdAndEstado(puntoId, "REGISTRADO") ||
                !rp.motivoOmision.isNullOrBlank()
            PuntoAcopioDto(
                id = puntoId,
                razonSocial = rp.punto.razonSocial,
                direccion = rp.punto.direccion,
                zona = rp.punto.zona,
                materialHabitual = rp.punto.materialHabitual,
                secuencia = rp.secuencia,
                atendido = atendido
            )
        }
        return RutaDiaDto(
            id = rutaId,
            codigo = ruta.codigo,
            fecha = ruta.fecha.toString(),
            zona = ruta.zona,
            estado = ruta.estado,
            operario = ruta.operarioUsername,
            puntos = puntos
        )
    }

    private fun aPuntoDto(punto: PuntoAcopio, secuencia: Int): PuntoAcopioDto {
        val id = punto.id ?: 0L
        val atendido = pesajeRepository.existsByPuntoIdAndEstado(id, "REGISTRADO")
        return PuntoAcopioDto(
            id = id,
            razonSocial = punto.razonSocial,
            direccion = punto.direccion,
            zona = punto.zona,
            materialHabitual = punto.materialHabitual,
            secuencia = secuencia,
            atendido = atendido
        )
    }

    private fun Pesaje.aDto(): PesajeDto = PesajeDto(
        id = this.id ?: 0L,
        puntoId = this.punto.id ?: 0L,
        fechaHora = this.fechaHora.truncatedTo(ChronoUnit.SECONDS).toString(),
        material = this.material,
        pesoBruto = this.pesoBruto.toDouble(),
        tara = this.tara.toDouble(),
        pesoNeto = this.pesoNeto.toDouble(),
        humedadEstimada = this.humedadEstimada.toDouble(),
        observacion = this.observacion,
        operario = this.operario,
        estado = this.estado,
        motivoAnulacion = this.motivoAnulacion
    )
}
