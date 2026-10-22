package pe.upeu.ecorecicla.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.upeu.ecorecicla.db.EcoReciclaDb
import pe.upeu.ecorecicla.db.Pesaje as FilaPesaje
import pe.upeu.ecorecicla.db.Punto_acopio as FilaPunto
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio

/**
 * ============================================================================
 * BASE DE DATOS LOCAL OFFLINE-FIRST CON SQLDELIGHT (SECCION 4.6 Y 4.7)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Preguntas 7, 8, 9 y Solicitudes SC-A, SC-C, SC-D):
 * - ¿Por que la pantalla principal observa la base local y no la respuesta del servicio?:
 *   Porque buena parte de la ruta transcurre en sotanos y zonas sin cobertura. Al observar
 *   `queries.filtrarPuntosRuta(...).asFlow().mapToList()`, la UI siempre pinta al instante
 *   lo que hay en SQLite y reacciona automaticamente tanto a registros hechos en modo avion
 *   como a los datos descargados al sincronizar.
 * - ¿Que queda guardado al registrar con modo avion?:
 *   Se calcula el siguiente ID local provisional negativo (`minOf(siguienteIdLocal(), 0L) - 1L`),
 *   que nunca colisiona con los IDs positivos del servidor, y se inserta con `pendiente = 1L`.
 *   Ademas, se marca el punto de acopio como `atendido = 1L` dentro de la misma transaccion.
 * - Al sincronizar (`reemplazarPendientePorDefinitivo`):
 *   Dentro de una misma transaccion se borra la fila provisional (id negativo) y se inserta
 *   la fila definitiva devuelta por el servidor (id positivo y `pendiente = 0L`), evitando
 *   duplicados o perdida de filas.
 */
class BaseLocal(
    private val db: EcoReciclaDb
) {
    private val queries = db.ecoReciclaQueries

    companion object {
        private const val CLAVE_ESTADO_RUTA = "estado_ruta"
        private const val CLAVE_ULTIMA_SYNC = "ultima_sincronizacion"
    }

    /** Flujo observable de todos los puntos de la ruta en orden de secuencia. */
    fun observarPuntosRuta(): Flow<List<PuntoAcopio>> =
        queries.listarPuntosRuta()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { filas -> filas.map { it.aDominio() } }

    /**
     * SC-A: Flujo observable de puntos de la ruta filtrados en SQL local por
     * texto de busqueda y por "Solo pendientes de pesar" (`soloPendientes`).
     */
    fun observarPuntosFiltrados(busqueda: String, soloPendientes: Boolean): Flow<List<PuntoAcopio>> =
        queries.filtrarPuntosRuta(
            soloPendientes = if (soloPendientes) 1L else 0L,
            busqueda = busqueda.trim()
        )
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { filas -> filas.map { it.aDominio() } }

    /** Flujo observable de los pesajes de un punto, del mas reciente al mas antiguo. */
    fun observarPesajesDePunto(puntoId: Long): Flow<List<Pesaje>> =
        queries.listarPesajesPorPunto(puntoId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { filas -> filas.map { it.aDominio() } }

    /**
     * SC-D: Flujo observable con el numero de pesajes pendientes de sincronizar (`pendiente = 1`).
     * Se actualiza automaticamente solo al registrar un pesaje offline o al sincronizar.
     */
    fun observarCantidadPendientes(): Flow<Long> =
        queries.contarPesajesPendientes()
            .asFlow()
            .mapToOne(Dispatchers.Default)

    fun obtenerPunto(puntoId: Long): PuntoAcopio? =
        queries.obtenerPuntoPorId(puntoId).executeAsOneOrNull()?.aDominio()

    fun listarPuntosActuales(): List<PuntoAcopio> =
        queries.listarPuntosRuta().executeAsList().map { it.aDominio() }

    fun listarPesajesPendientes(): List<Pesaje> =
        queries.listarPesajesPendientes().executeAsList().map { it.aDominio() }

    /**
     * Guarda un pesaje nuevo con identificador local provisional negativo (-1, -2, ...)
     * y marca `pendiente = 1`. Tambien marca el punto como atendido en la ruta local.
     */
    fun guardarPesajePendiente(
        puntoId: Long,
        fechaHora: String,
        material: String,
        pesoBruto: Double,
        tara: Double,
        humedadEstimada: Double,
        observacion: String?,
        operario: String
    ): Pesaje {
        var creado: Pesaje? = null
        queries.transaction {
            val menorActual = queries.siguienteIdLocal().executeAsOne()
            val idLocalNegativo = minOf(menorActual, 0L) - 1L
            val pesoNetoLocal = ((pesoBruto - tara) * 100.0).toLong() / 100.0

            queries.insertarPesaje(
                id = idLocalNegativo,
                punto_id = puntoId,
                fecha_hora = fechaHora,
                material = material,
                peso_bruto = pesoBruto,
                tara = tara,
                peso_neto = pesoNetoLocal,
                humedad_estimada = humedadEstimada,
                observacion = observacion,
                operario = operario,
                estado = "REGISTRADO",
                motivo_anulacion = null,
                pendiente = 1L
            )
            queries.marcarPuntoAtendido(atendido = 1L, id = puntoId)

            creado = Pesaje(
                id = idLocalNegativo,
                puntoId = puntoId,
                fechaHora = fechaHora,
                material = material,
                pesoBruto = pesoBruto,
                tara = tara,
                pesoNeto = pesoNetoLocal,
                operario = operario,
                estado = EstadoPesaje.Registrado,
                pendiente = true,
                observacion = observacion,
                humedadEstimada = humedadEstimada
            )
        }
        return creado!!
    }

    /**
     * Sustituye dentro de la misma transaccion la fila provisional (id negativo)
     * por la fila definitiva devuelta por el servidor (201 Created, id positivo, pendiente = 0).
     */
    fun reemplazarPendientePorDefinitivo(idLocalNegativo: Long, definitivo: Pesaje) {
        queries.transaction {
            queries.eliminarPesajePorId(idLocalNegativo)
            insertarFilaPesaje(definitivo.copy(pendiente = false))
            queries.marcarPuntoAtendido(atendido = 1L, id = definitivo.puntoId)
        }
    }

    /** Elimina un pesaje provisional si el servidor lo rechazo por regla de negocio o validacion. */
    fun eliminarPesajeLocal(id: Long) {
        queries.eliminarPesajePorId(id)
    }

    /** Guarda o actualiza un pesaje ya sincronizado (`pendiente = 0`). */
    fun guardarPesajeSincronizado(pesaje: Pesaje) {
        queries.transaction {
            insertarFilaPesaje(pesaje.copy(pendiente = false))
        }
    }

    /** Reemplaza los puntos de la ruta manteniendo como atendidos aquellos con pesajes pendientes locales. */
    fun reemplazarPuntosRuta(puntosServidor: List<PuntoAcopio>, estadoRuta: String, fechaHoraSync: String) {
        queries.transaction {
            val puntosConPendientes = queries.listarPesajesPendientes().executeAsList()
                .map { it.punto_id }
                .toSet()

            queries.borrarPuntos()
            puntosServidor.forEach { p ->
                val estaAtendido = p.atendido || puntosConPendientes.contains(p.id)
                queries.insertarPunto(
                    id = p.id,
                    razon_social = p.razonSocial,
                    direccion = p.direccion,
                    zona = p.zona,
                    material_habitual = p.materialHabitual,
                    secuencia = p.secuencia.toLong(),
                    atendido = if (estaAtendido) 1L else 0L
                )
            }
            queries.guardarMeta(CLAVE_ESTADO_RUTA, estadoRuta)
            queries.guardarMeta(CLAVE_ULTIMA_SYNC, fechaHoraSync)
        }
    }

    /** Reemplaza los pesajes sincronizados de un punto sin tocar los que aun esten pendientes (`pendiente = 1`). */
    fun reemplazarPesajesSincronizadosDePunto(puntoId: Long, pesajesServidor: List<Pesaje>) {
        queries.transaction {
            queries.borrarPesajesSincronizadosDePunto(puntoId)
            pesajesServidor.forEach { insertarFilaPesaje(it.copy(pendiente = false)) }
        }
    }

    fun guardarEstadoRuta(estado: String) {
        queries.guardarMeta(CLAVE_ESTADO_RUTA, estado)
    }

    fun leerEstadoRuta(): String =
        queries.leerMeta(CLAVE_ESTADO_RUTA).executeAsOneOrNull() ?: "ABIERTA"

    fun leerUltimaSincronizacion(): String? =
        queries.leerMeta(CLAVE_ULTIMA_SYNC).executeAsOneOrNull()

    fun limpiarTodoAlCerrarSesion() {
        queries.transaction {
            queries.borrarTodosPesajesSincronizados()
        }
    }

    private fun insertarFilaPesaje(pesaje: Pesaje) {
        val (estadoStr, motivoStr) = when (val est = pesaje.estado) {
            is EstadoPesaje.Registrado -> "REGISTRADO" to null
            is EstadoPesaje.Anulado -> "ANULADO" to est.motivo
        }
        queries.insertarPesaje(
            id = pesaje.id,
            punto_id = pesaje.puntoId,
            fecha_hora = pesaje.fechaHora,
            material = pesaje.material,
            peso_bruto = pesaje.pesoBruto,
            tara = pesaje.tara,
            peso_neto = pesaje.pesoNeto,
            humedad_estimada = pesaje.humedadEstimada,
            observacion = pesaje.observacion,
            operario = pesaje.operario,
            estado = estadoStr,
            motivo_anulacion = motivoStr,
            pendiente = if (pesaje.pendiente) 1L else 0L
        )
    }
}

private fun FilaPunto.aDominio(): PuntoAcopio = PuntoAcopio(
    id = id,
    razonSocial = razon_social,
    direccion = direccion,
    zona = zona,
    materialHabitual = material_habitual,
    secuencia = secuencia.toInt(),
    atendido = atendido != 0L
)

private fun FilaPesaje.aDominio(): Pesaje = Pesaje(
    id = id,
    puntoId = punto_id,
    fechaHora = fecha_hora,
    material = material,
    pesoBruto = peso_bruto,
    tara = tara,
    pesoNeto = peso_neto,
    operario = operario,
    estado = if (estado.equals("ANULADO", ignoreCase = true)) {
        EstadoPesaje.Anulado(motivo_anulacion ?: "Anulado")
    } else {
        EstadoPesaje.Registrado
    },
    pendiente = pendiente != 0L,
    observacion = observacion,
    humedadEstimada = humedad_estimada
)
