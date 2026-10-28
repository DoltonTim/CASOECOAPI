package pe.upeu.ecorecicla

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import pe.upeu.ecorecicla.dominio.Pesaje
import pe.upeu.ecorecicla.dominio.PuntoAcopio
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
import pe.upeu.ecorecicla.seguridad.PrivilegiosPorRol
import pe.upeu.ecorecicla.servicio.EcoReciclaService
import pe.upeu.ecorecicla.web.dto.PesajeRequestDto
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

/**
 * Pruebas unitarias de las reglas de negocio RN-01 a RN-04 y de la matriz de privilegios.
 */
class EcoReciclaServiceTest {

    private val puntoRepo = mock(PuntoAcopioRepository::class.java)
    private val rutaRepo = mock(RutaDiaRepository::class.java)
    private val rutaPuntoRepo = mock(RutaPuntoRepository::class.java)
    private val pesajeRepo = mock(PesajeRepository::class.java)

    private val service = EcoReciclaService(puntoRepo, rutaRepo, rutaPuntoRepo, pesajeRepo)

    @Test
    fun `RN-01 rechaza con 409 registrar pesaje en una ruta cerrada`() {
        val punto = PuntoAcopio(id = 7L, razonSocial = "Metalurgica", zona = "Norte")
        val rutaCerrada = RutaDia(id = 2L, codigo = "RUTA-AYER-01", estado = "CERRADA")
        val asignacion = RutaPunto(id = 1L, ruta = rutaCerrada, punto = punto, secuencia = 1)

        `when`(puntoRepo.findById(7L)).thenReturn(Optional.of(punto))
        `when`(rutaPuntoRepo.findFirstByPuntoIdOrderByRutaIdAsc(7L)).thenReturn(Optional.of(asignacion))

        val ex = assertThrows(ReglaNegocioException::class.java) {
            service.registrarPesaje(
                PesajeRequestDto(puntoId = 7L, material = "Aluminio", pesoBruto = 80.0, tara = 10.0),
                "operario1"
            )
        }
        assertTrue(ex.message!!.contains("RN-01"))
    }

    @Test
    fun `RN-02 rechaza con 403 cuando un operario intenta corregir el pesaje de otro operario`() {
        val rutaAbierta = RutaDia(id = 1L, codigo = "RUTA-HOY-01", estado = "ABIERTA")
        val punto = PuntoAcopio(id = 2L, razonSocial = "Textiles", zona = "Norte")
        val pesajeDeOtro = Pesaje(
            id = 4L,
            punto = punto,
            ruta = rutaAbierta,
            fechaHora = LocalDateTime.now(),
            material = "Carton",
            pesoBruto = BigDecimal("100.00"),
            tara = BigDecimal("10.00"),
            pesoNeto = BigDecimal("90.00"),
            operario = "operario2",
            estado = "REGISTRADO"
        )

        `when`(pesajeRepo.findById(4L)).thenReturn(Optional.of(pesajeDeOtro))

        val privilegiosOperario = PrivilegiosPorRol.nombresDePrivilegios("OPERARIO")
        val ex = assertThrows(SinPrivilegioException::class.java) {
            service.corregirPesaje(
                id = 4L,
                request = PesajeRequestDto(puntoId = 2L, material = "Carton", pesoBruto = 110.0, tara = 10.0),
                usuarioAutenticado = "operario1",
                privilegiosUsuario = privilegiosOperario
            )
        }
        assertTrue(ex.message!!.contains("RN-02"))
    }

    @Test
    fun `RN-03 rechaza con 400 cuando la tara es mayor o igual al peso bruto`() {
        val punto = PuntoAcopio(id = 1L, razonSocial = "Plasticos", zona = "Norte")
        val rutaAbierta = RutaDia(id = 1L, codigo = "RUTA-HOY-01", estado = "ABIERTA")
        val asignacion = RutaPunto(id = 1L, ruta = rutaAbierta, punto = punto, secuencia = 1)

        `when`(puntoRepo.findById(1L)).thenReturn(Optional.of(punto))
        `when`(rutaPuntoRepo.findFirstByPuntoIdOrderByRutaIdAsc(1L)).thenReturn(Optional.of(asignacion))

        val ex = assertThrows(ValidacionCampoException::class.java) {
            service.registrarPesaje(
                PesajeRequestDto(puntoId = 1L, material = "PET", pesoBruto = 15.0, tara = 20.0),
                "operario1"
            )
        }
        assertEquals("tara", ex.campo)
        assertTrue(ex.message!!.contains("RN-03"))
    }

    @Test
    fun `RN-04 rechaza con 409 cerrar ruta mientras existan puntos sin pesar ni motivo de omision`() {
        val rutaAbierta = RutaDia(id = 1L, codigo = "RUTA-HOY-01", fecha = LocalDate.now(), estado = "ABIERTA")
        val p1 = PuntoAcopio(id = 1L, razonSocial = "P1")
        val p2 = PuntoAcopio(id = 5L, razonSocial = "P5")

        `when`(rutaRepo.findById(1L)).thenReturn(Optional.of(rutaAbierta))
        `when`(rutaPuntoRepo.findByRutaIdOrderBySecuenciaAsc(1L)).thenReturn(
            listOf(
                RutaPunto(id = 1L, ruta = rutaAbierta, punto = p1, secuencia = 1, motivoOmision = null),
                RutaPunto(id = 2L, ruta = rutaAbierta, punto = p2, secuencia = 5, motivoOmision = null)
            )
        )
        `when`(pesajeRepo.existsByPuntoIdAndEstado(1L, "REGISTRADO")).thenReturn(true)
        `when`(pesajeRepo.existsByPuntoIdAndEstado(5L, "REGISTRADO")).thenReturn(false)

        val ex = assertThrows(ReglaNegocioException::class.java) {
            service.cerrarRuta(1L)
        }
        assertTrue(ex.message!!.contains("RN-04"))
        assertTrue(ex.message!!.contains("1 punto(s)"))
    }

    @Test
    fun `SC-B matriz de privilegios asigna solo lectura al rol AUDITOR`() {
        val privilegiosAuditor = PrivilegiosPorRol.deRol("AUDITOR")
        assertTrue(privilegiosAuditor.contains(Privilegio.PUNTO_LEER))
        assertTrue(privilegiosAuditor.contains(Privilegio.PESAJE_LEER))
        assertFalse(privilegiosAuditor.contains(Privilegio.PESAJE_CREAR))
        assertFalse(privilegiosAuditor.contains(Privilegio.PESAJE_EDITAR))
        assertFalse(privilegiosAuditor.contains(Privilegio.PESAJE_ANULAR))
        assertFalse(privilegiosAuditor.contains(Privilegio.RUTA_CERRAR))
    }
}

// Pruebas de solicitud de cambio en rama DOLTON-MEZA completadas
