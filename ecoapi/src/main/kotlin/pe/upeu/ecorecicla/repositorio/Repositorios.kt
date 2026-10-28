package pe.upeu.ecorecicla.repositorio

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import pe.upeu.ecorecicla.dominio.Pesaje
import pe.upeu.ecorecicla.dominio.PuntoAcopio
import pe.upeu.ecorecicla.dominio.RutaDia
import pe.upeu.ecorecicla.dominio.RutaPunto
import java.time.LocalDate
import java.util.Optional

@Repository
interface PuntoAcopioRepository : JpaRepository<PuntoAcopio, Long> {

    @Query(
        """
        SELECT p FROM PuntoAcopio p
        WHERE p.activo = true
          AND (:zona IS NULL OR :zona = '' OR UPPER(p.zona) = UPPER(:zona))
        ORDER BY p.secuencia ASC, p.id ASC
        """
    )
    fun buscarPorZona(
        @Param("zona") zona: String?,
        pageable: Pageable
    ): Page<PuntoAcopio>
}

@Repository
interface RutaDiaRepository : JpaRepository<RutaDia, Long> {
    fun findFirstByFechaOrderByIdAsc(fecha: LocalDate): Optional<RutaDia>
    fun findFirstByEstadoOrderByFechaDescIdAsc(estado: String): Optional<RutaDia>
}

@Repository
interface RutaPuntoRepository : JpaRepository<RutaPunto, Long> {
    fun findByRutaIdOrderBySecuenciaAsc(rutaId: Long): List<RutaPunto>
    fun findFirstByPuntoIdOrderByRutaIdAsc(puntoId: Long): Optional<RutaPunto>
}

@Repository
interface PesajeRepository : JpaRepository<Pesaje, Long> {

    /**
     * Listado paginado de los pesajes de un punto, del mas reciente al mas antiguo (Seccion 4.3).
     */
    fun findByPuntoIdOrderByFechaHoraDescIdDesc(puntoId: Long, pageable: Pageable): Page<Pesaje>

    fun findByPuntoIdOrderByFechaHoraDescIdDesc(puntoId: Long): List<Pesaje>

    fun existsByPuntoIdAndEstado(puntoId: Long, estado: String): Boolean
}
