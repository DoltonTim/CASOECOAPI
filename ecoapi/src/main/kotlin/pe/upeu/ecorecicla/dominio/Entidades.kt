package pe.upeu.ecorecicla.dominio

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * ============================================================================
 * ENTIDADES DEL DOMINIO Y EXCEPCIONES DE NEGOCIO (SECCION 4.7)
 * ============================================================================
 */

@Entity
@Table(name = "puntos_acopio")
class PuntoAcopio(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "razon_social", nullable = false, length = 150)
    var razonSocial: String = "",

    @Column(nullable = false, length = 200)
    var direccion: String = "",

    @Column(nullable = false, length = 50)
    var zona: String = "",

    @Column(name = "material_habitual", nullable = false, length = 80)
    var materialHabitual: String = "",

    @Column(nullable = false)
    var secuencia: Int = 1,

    @Column(nullable = false)
    var activo: Boolean = true
)

@Entity
@Table(name = "rutas_dia")
class RutaDia(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 30)
    var codigo: String = "",

    @Column(nullable = false)
    var fecha: LocalDate = LocalDate.now(),

    @Column(nullable = false, length = 50)
    var zona: String = "Multizona",

    @Column(nullable = false, length = 20)
    var estado: String = "ABIERTA", // ABIERTA | CERRADA

    @Column(name = "operario_username", nullable = false, length = 50)
    var operarioUsername: String = "operario1"
)

@Entity
@Table(name = "ruta_puntos")
class RutaPunto(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ruta_id", nullable = false)
    var ruta: RutaDia = RutaDia(),

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "punto_id", nullable = false)
    var punto: PuntoAcopio = PuntoAcopio(),

    @Column(nullable = false)
    var secuencia: Int = 1,

    @Column(name = "motivo_omision", length = 200)
    var motivoOmision: String? = null
)

@Entity
@Table(name = "pesajes")
class Pesaje(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "punto_id", nullable = false)
    var punto: PuntoAcopio = PuntoAcopio(),

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ruta_id", nullable = false)
    var ruta: RutaDia = RutaDia(),

    @Column(name = "fecha_hora", nullable = false)
    var fechaHora: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false, length = 80)
    var material: String = "",

    @Column(name = "peso_bruto", nullable = false, precision = 10, scale = 2)
    var pesoBruto: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 10, scale = 2)
    var tara: BigDecimal = BigDecimal.ZERO,

    @Column(name = "peso_neto", nullable = false, precision = 10, scale = 2)
    var pesoNeto: BigDecimal = BigDecimal.ZERO,

    @Column(name = "humedad_estimada", nullable = false, precision = 5, scale = 2)
    var humedadEstimada: BigDecimal = BigDecimal.ZERO,

    @Column(length = 250)
    var observacion: String? = null,

    @Column(nullable = false, length = 50)
    var operario: String = "",

    @Column(nullable = false, length = 20)
    var estado: String = "REGISTRADO", // REGISTRADO | ANULADO

    @Column(name = "motivo_anulacion", length = 250)
    var motivoAnulacion: String? = null
)

/** Excepcion para recursos inexistentes -> 404 Not Found */
class RecursoNoEncontradoException(mensaje: String) : RuntimeException(mensaje)

/** Excepcion para reglas de negocio RN-01 y RN-04 -> 409 Conflict */
class ReglaNegocioException(mensaje: String) : RuntimeException(mensaje)

/** Excepcion para falta de privilegio o incumplimiento de RN-02 -> 403 Forbidden */
class SinPrivilegioException(mensaje: String) : RuntimeException(mensaje)

/** Excepcion para validacion de un campo especifico (ej. RN-03 pesoNeto <= 0) -> 400 Bad Request */
class ValidacionCampoException(val campo: String, mensaje: String) : RuntimeException(mensaje)
