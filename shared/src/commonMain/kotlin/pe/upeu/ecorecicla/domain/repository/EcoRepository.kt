package pe.upeu.ecorecicla.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.ResumenSincronizacion

/**
 * Contrato del repositorio offline-first para la ruta del dia, puntos de acopio y pesajes (Seccion 4.7).
 * Solo declara contratos del dominio: nunca expone DTOs ni tipos de Ktor o SQLDelight.
 */
interface EcoRepository {
    /** Flujo observable de los puntos de la ruta desde la base local (RF-A03). */
    fun observarPuntosRuta(): Flow<List<PuntoAcopio>>

    /** SC-A: Flujo observable filtrado en SQL local por buscador y "Solo pendientes de pesar". */
    fun observarPuntosFiltrados(busqueda: String, soloPendientes: Boolean): Flow<List<PuntoAcopio>>

    /** Flujo observable de los pesajes de un punto desde la base local (RF-A04). */
    fun observarPesajesDePunto(puntoId: Long): Flow<List<Pesaje>>

    /** SC-D: Flujo observable con la cantidad de pesajes pendientes de sincronizar. */
    fun observarCantidadPendientes(): Flow<Long>

    /** Obtiene un punto de acopio desde la base local. */
    fun obtenerPuntoLocal(puntoId: Long): PuntoAcopio?

    /** Estado actual de la ruta guardado localmente (ABIERTA / CERRADA). */
    fun leerEstadoRuta(): String

    /** Marca de la ultima sincronizacion exitosa guardada localmente. */
    fun leerUltimaSincronizacion(): String?

    /** RF-A05: Registra un pesaje (offline-first: guarda como pendiente con id negativo e intenta enviar). */
    suspend fun registrarPesaje(
        puntoId: Long,
        material: String,
        pesoBruto: Double,
        tara: Double,
        humedadEstimada: Double = 0.0,
        observacion: String? = null
    ): Result<Pesaje>

    /** RF-A05 / RF-B05: Corrige un pesaje existente (sujeto a RN-01, RN-02 y RN-03). */
    suspend fun corregirPesaje(
        id: Long,
        puntoId: Long,
        material: String,
        pesoBruto: Double,
        tara: Double,
        humedadEstimada: Double = 0.0,
        observacion: String? = null
    ): Result<Pesaje>

    /** RF-A04 / RF-B05: Anula un pesaje marcandolo con motivo sin borrarlo. */
    suspend fun anularPesaje(id: Long, puntoId: Long, motivo: String): Result<Pesaje>

    /** RF-B06: Cierra la ruta del dia en el servidor (sujeto a RN-04). */
    suspend fun cerrarRutaDelDia(): Result<String>

    /** Refresca los pesajes de un punto desde el servidor hacia la base local. */
    suspend fun refrescarPesajesDePunto(puntoId: Long): Result<List<Pesaje>>

    /** RF-A06: Sincronizacion completa: primero sube los pendientes y despues baja lo actualizado. */
    suspend fun sincronizar(): Result<ResumenSincronizacion>
}
