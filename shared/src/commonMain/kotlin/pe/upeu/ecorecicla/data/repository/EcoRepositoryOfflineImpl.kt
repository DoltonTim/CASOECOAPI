package pe.upeu.ecorecicla.data.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.local.BaseLocal
import pe.upeu.ecorecicla.data.remote.EcoApiService
import pe.upeu.ecorecicla.data.remote.dto.PesajeRequestDto
import pe.upeu.ecorecicla.data.remote.ejecutarLlamadaRemota
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.ResumenSincronizacion
import pe.upeu.ecorecicla.domain.repository.EcoRepository

/**
 * ============================================================================
 * REPOSITORIO OFFLINE-FIRST DE RUTA, PUNTOS Y PESAJES (SECCION 4.4 Y 4.7)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Preguntas 6, 7, 8 y 9):
 * - Recorrido completo de un registro (Pregunta 6):
 *   Tabla Oracle (`pesajes`) -> `PesajeRepository` -> `EcoReciclaService` ->
 *   `PesajeController` (`/api/v1/puntos/{id}/pesajes`) -> `EcoApiService` ->
 *   `PesajeDto.aDominio()` -> `EcoRepositoryOfflineImpl` -> `BaseLocal` (SQLDelight `pesaje`) ->
 *   `observarPesajesDePunto()` (`Flow<List<Pesaje>>`) -> `ObservarPesajesPuntoUseCase` ->
 *   `DetalleViewModel` (`StateFlow<DetalleUiState>`) -> `DetallePuntoScreen` (Composable que lo dibuja).
 * - Registro con modo avion activado (Pregunta 8):
 *   1. `registrarPesaje` guarda primero la fila en `BaseLocal` con `pendiente = true` y un
 *      identificador negativo provisional (`-1`, `-2`, ...).
 *   2. Intenta enviarlo al servidor. Si no hay red (`ErrorApi.SinConexion` / `TiempoAgotado`),
 *      NO falla: devuelve `Result.success(local)` con `pendiente = true` para que el operario
 *      siga trabajando en el sotano.
 *   3. Si el servidor responde `400 Validacion` (ej. RN-03 tara >= bruto), `403 SinPrivilegio` o
 *      `409 Conflicto` (RN-01 ruta cerrada), se borra la fila provisional y se propaga el error
 *      al ViewModel para mostrar el mensaje debajo del campo o el motivo del servidor.
 * - Orden de la sincronizacion (Pregunta 9):
 *   1. PRIMERO (`subirPendientes()`): envia cada fila con `pendiente = 1` al servidor (`POST /pesajes`)
 *      y sustituye la fila provisional negativa por la definitiva positiva (`201 Created`).
 *   2. DESPUES (`descargarActualizado()`): descarga la ruta del dia (`GET /rutas/hoy`) y los pesajes
 *      de cada punto (`GET /puntos/{id}/pesajes`) actualizando las filas sincronizadas.
 *   - ¿Que pasaria si invirtieras ese orden?:
 *     Si bajaras primero antes de subir lo pendiente, el estado del servidor (donde ese punto aun
 *     figura como "no atendido" y sin ese pesaje) pisaria o desincronizaria el estado local antes
 *     de que el servidor conozca los pesajes hechos en el sotano.
 */
class EcoRepositoryOfflineImpl(
    private val local: BaseLocal,
    private val remoto: EcoApiService,
    private val almacenSeguro: AlmacenSeguro? = null
) : EcoRepository {

    override fun observarPuntosRuta(): Flow<List<PuntoAcopio>> =
        local.observarPuntosRuta()

    override fun observarPuntosFiltrados(busqueda: String, soloPendientes: Boolean): Flow<List<PuntoAcopio>> =
        local.observarPuntosFiltrados(busqueda, soloPendientes)

    override fun observarPesajesDePunto(puntoId: Long): Flow<List<Pesaje>> =
        local.observarPesajesDePunto(puntoId)

    override fun observarCantidadPendientes(): Flow<Long> =
        local.observarCantidadPendientes()

    override fun obtenerPuntoLocal(puntoId: Long): PuntoAcopio? =
        local.obtenerPunto(puntoId)

    override fun leerEstadoRuta(): String =
        local.leerEstadoRuta()

    override fun leerUltimaSincronizacion(): String? =
        local.leerUltimaSincronizacion()

    override suspend fun registrarPesaje(
        puntoId: Long,
        material: String,
        pesoBruto: Double,
        tara: Double,
        humedadEstimada: Double,
        observacion: String?
    ): Result<Pesaje> {
        val operarioActual = almacenSeguro?.leerSesionCache()?.usuario ?: "operario1"
        val fechaHoraIso = "2026-10-28T14:30:00"

        // 1. Guardamos inmediatamente en la base local como pendiente con ID negativo
        val provisional = local.guardarPesajePendiente(
            puntoId = puntoId,
            fechaHora = fechaHoraIso,
            material = material.trim(),
            pesoBruto = pesoBruto,
            tara = tara,
            humedadEstimada = humedadEstimada,
            observacion = observacion?.trim()?.takeIf { it.isNotEmpty() },
            operario = operarioActual
        )

        // 2. Intentamos enviarlo al servidor
        val resultadoRemoto = ejecutarLlamadaRemota(almacenSeguro) {
            remoto.registrarPesaje(
                PesajeRequestDto(
                    puntoId = puntoId,
                    material = material.trim(),
                    pesoBruto = pesoBruto,
                    tara = tara,
                    humedadEstimada = humedadEstimada,
                    observacion = observacion?.trim()?.takeIf { it.isNotEmpty() }
                )
            ).aDominio(pendiente = false)
        }

        return resultadoRemoto.fold(
            onSuccess = { definitivo ->
                // 201 Created: se sustituye la fila provisional por la definitiva y se limpia pendiente
                local.reemplazarPendientePorDefinitivo(provisional.id, definitivo)
                Result.success(definitivo)
            },
            onFailure = { error ->
                when (error) {
                    is ErrorApi.SinConexion, is ErrorApi.TiempoAgotado -> {
                        // Sin conexion (modo avion): la fila queda guardada como pendiente con id negativo
                        Result.success(provisional)
                    }
                    else -> {
                        // Error del servidor (400 RN-03, 401, 403, 409 RN-01): revertimos el provisional
                        // y devolvemos el error tipado para que la UI lo muestre
                        local.eliminarPesajeLocal(provisional.id)
                        Result.failure(error)
                    }
                }
            }
        )
    }

    override suspend fun corregirPesaje(
        id: Long,
        puntoId: Long,
        material: String,
        pesoBruto: Double,
        tara: Double,
        humedadEstimada: Double,
        observacion: String?
    ): Result<Pesaje> {
        // Si el pesaje aun es local provisional (id < 0), lo actualizamos en SQLDelight e intentamos subir
        if (id < 0) {
            local.eliminarPesajeLocal(id)
            return registrarPesaje(puntoId, material, pesoBruto, tara, humedadEstimada, observacion)
        }

        return ejecutarLlamadaRemota(almacenSeguro) {
            val dto = remoto.corregirPesaje(
                id = id,
                request = PesajeRequestDto(
                    puntoId = puntoId,
                    material = material.trim(),
                    pesoBruto = pesoBruto,
                    tara = tara,
                    humedadEstimada = humedadEstimada,
                    observacion = observacion?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
            val actualizado = dto.aDominio(pendiente = false)
            local.guardarPesajeSincronizado(actualizado)
            actualizado
        }
    }

    override suspend fun anularPesaje(id: Long, puntoId: Long, motivo: String): Result<Pesaje> =
        ejecutarLlamadaRemota(almacenSeguro) {
            val dto = remoto.anularPesaje(id = id, motivo = motivo.trim())
            val anulado = dto.aDominio(pendiente = false)
            local.guardarPesajeSincronizado(anulado)
            anulado
        }

    override suspend fun cerrarRutaDelDia(): Result<String> =
        ejecutarLlamadaRemota(almacenSeguro) {
            val rutaDto = remoto.cerrarRutaHoy()
            local.guardarEstadoRuta(rutaDto.estado)
            rutaDto.estado
        }

    override suspend fun refrescarPesajesDePunto(puntoId: Long): Result<List<Pesaje>> =
        ejecutarLlamadaRemota(almacenSeguro) {
            val pagina = remoto.listarPesajesDePunto(puntoId = puntoId, pagina = 0, tamanio = 50)
            val lista = pagina.contenido.map { it.aDominio(pendiente = false) }
            local.reemplazarPesajesSincronizadosDePunto(puntoId, lista)
            lista
        }

    /**
     * RF-A06: Sincronizacion offline-first.
     * Orden estricto:
     * 1. PRIMERO envia los pesajes pendientes (`pendiente = 1`, id negativo).
     * 2. DESPUES descarga la ruta del dia y los pesajes actualizados del servidor.
     */
    override suspend fun sincronizar(): Result<ResumenSincronizacion> =
        ejecutarLlamadaRemota(almacenSeguro) {
            // PASO 1: Subir lo pendiente al servidor antes de bajar
            val pendientes = local.listarPesajesPendientes()
            var enviados = 0
            for (pendiente in pendientes) {
                try {
                    val creado = remoto.registrarPesaje(
                        PesajeRequestDto(
                            puntoId = pendiente.puntoId,
                            material = pendiente.material,
                            pesoBruto = pendiente.pesoBruto,
                            tara = pendiente.tara,
                            humedadEstimada = pendiente.humedadEstimada,
                            observacion = pendiente.observacion
                        )
                    ).aDominio(pendiente = false)
                    local.reemplazarPendientePorDefinitivo(pendiente.id, creado)
                    enviados++
                } catch (_: ErrorApi.Conflicto) {
                    // Si la ruta se cerro en el servidor (409), limpiamos la marca de pendiente
                    local.eliminarPesajeLocal(pendiente.id)
                }
            }

            // PASO 2: Traer la ruta del dia actualizada y los pesajes de cada punto
            val rutaDto = remoto.obtenerRutaHoy()
            val puntosDominio = rutaDto.puntos.map { it.aDominio() }
            val marcaHora = "Sincronizado (${rutaDto.fecha})"
            local.reemplazarPuntosRuta(puntosDominio, rutaDto.estado, marcaHora)

            var totalPesajesRecibidos = 0
            for (punto in puntosDominio) {
                val paginaPesajes = remoto.listarPesajesDePunto(punto.id, pagina = 0, tamanio = 50)
                val pesajesPunto = paginaPesajes.contenido.map { it.aDominio(pendiente = false) }
                local.reemplazarPesajesSincronizadosDePunto(punto.id, pesajesPunto)
                totalPesajesRecibidos += pesajesPunto.size
            }

            ResumenSincronizacion(
                enviados = enviados,
                puntosRecibidos = puntosDominio.size,
                pesajesRecibidos = totalPesajesRecibidos,
                estadoRuta = rutaDto.estado,
                fechaHoraSincronizacion = marcaHora
            )
        }
}
