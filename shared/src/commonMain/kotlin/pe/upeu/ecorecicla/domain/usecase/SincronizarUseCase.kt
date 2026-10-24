package pe.upeu.ecorecicla.domain.usecase

import kotlinx.coroutines.flow.Flow
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.ResumenSincronizacion
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.repository.AuthRepository
import pe.upeu.ecorecicla.domain.repository.EcoRepository

/**
 * ============================================================================
 * CASOS DE USO DEL DOMINIO (SECCION 4.7)
 * ============================================================================
 * Coordina las operaciones entre la capa de presentacion (`ViewModel`) y los
 * contratos del repositorio (`EcoRepository` y `AuthRepository`).
 */

/**
 * Caso de uso explicito exigido en la Seccion 4.7: `SincronizarUseCase.kt`.
 * Ejecuta la sincronizacion offline-first (primero sube pendientes, luego descarga ruta y pesajes).
 */
class SincronizarUseCase(
    private val ecoRepository: EcoRepository
) {
    suspend operator fun invoke(): Result<ResumenSincronizacion> =
        ecoRepository.sincronizar()
}

class IniciarSesionUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(usuario: String, clave: String): Result<Sesion> =
        authRepository.iniciarSesion(usuario.trim(), clave)
}

class RecuperarSesionUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<Sesion?> =
        authRepository.recuperarSesion()
}

class CerrarSesionUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke() =
        authRepository.cerrarSesion()
}

/**
 * Caso de uso para observar la ruta del dia desde la base local.
 * Incluye la resolucion del filtro "Solo pendientes de pesar" combinado con el buscador (SC-A)
 * y el flujo observable del distintivo de pesajes pendientes de sincronizar (SC-D).
 */
class ObservarRutaUseCase(
    private val ecoRepository: EcoRepository
) {
    operator fun invoke(busqueda: String = "", soloPendientes: Boolean = false): Flow<List<PuntoAcopio>> =
        ecoRepository.observarPuntosFiltrados(busqueda.trim(), soloPendientes)

    fun observarPendientesSincronizar(): Flow<Long> =
        ecoRepository.observarCantidadPendientes()

    fun estadoRuta(): String = ecoRepository.leerEstadoRuta()

    fun ultimaSincronizacion(): String? = ecoRepository.leerUltimaSincronizacion()
}

class ObservarPesajesPuntoUseCase(
    private val ecoRepository: EcoRepository
) {
    operator fun invoke(puntoId: Long): Flow<List<Pesaje>> =
        ecoRepository.observarPesajesDePunto(puntoId)

    fun obtenerPunto(puntoId: Long): PuntoAcopio? =
        ecoRepository.obtenerPuntoLocal(puntoId)

    suspend fun refrescar(puntoId: Long): Result<List<Pesaje>> =
        ecoRepository.refrescarPesajesDePunto(puntoId)
}

class RegistrarPesajeUseCase(
    private val ecoRepository: EcoRepository
) {
    suspend operator fun invoke(
        puntoId: Long,
        material: String,
        pesoBruto: Double,
        tara: Double,
        humedadEstimada: Double = 0.0,
        observacion: String? = null
    ): Result<Pesaje> = ecoRepository.registrarPesaje(
        puntoId = puntoId,
        material = material,
        pesoBruto = pesoBruto,
        tara = tara,
        humedadEstimada = humedadEstimada,
        observacion = observacion
    )
}

class CorregirPesajeUseCase(
    private val ecoRepository: EcoRepository
) {
    suspend operator fun invoke(
        id: Long,
        puntoId: Long,
        material: String,
        pesoBruto: Double,
        tara: Double,
        humedadEstimada: Double = 0.0,
        observacion: String? = null
    ): Result<Pesaje> = ecoRepository.corregirPesaje(
        id = id,
        puntoId = puntoId,
        material = material,
        pesoBruto = pesoBruto,
        tara = tara,
        humedadEstimada = humedadEstimada,
        observacion = observacion
    )
}

class AnularPesajeUseCase(
    private val ecoRepository: EcoRepository
) {
    suspend operator fun invoke(id: Long, puntoId: Long, motivo: String): Result<Pesaje> =
        ecoRepository.anularPesaje(id, puntoId, motivo)
}

class CerrarRutaUseCase(
    private val ecoRepository: EcoRepository
) {
    suspend operator fun invoke(): Result<String> =
        ecoRepository.cerrarRutaDelDia()
}
