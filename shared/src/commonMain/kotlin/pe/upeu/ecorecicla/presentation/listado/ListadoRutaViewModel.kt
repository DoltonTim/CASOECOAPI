package pe.upeu.ecorecicla.presentation.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.ResumenSincronizacion
import pe.upeu.ecorecicla.domain.usecase.CerrarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.SincronizarUseCase

/**
 * ============================================================================
 * ESTADO Y VIEWMODEL DE LA RUTA DEL DIA (RF-A03, RF-A06 + SC-A Y SC-D)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Preguntas 5, 7, 9, SC-A y SC-D):
 * - Pregunta 7: La pantalla principal observa el `Flow<List<PuntoAcopio>>` de la
 *   base de datos local SQLDelight (`observarRutaUseCase(busqueda, soloPendientes)`),
 *   NUNCA la respuesta directa de la red.
 * - SC-A: El filtro «Solo pendientes de pesar» combinado con el buscador se resuelve
 *   en el flujo de consulta SQL local (`flatMapLatest`), NO recorriendo la lista en el composable.
 * - SC-D: El distintivo con el numero de pesajes pendientes de sincronizar proviene
 *   del flujo observable `observarRutaUseCase.observarPendientesSincronizar()` (`SELECT COUNT(*)
 *   FROM pesaje WHERE pendiente = 1`), NO de un contador mantenido a mano en el ViewModel.
 */
data class ListadoRutaUiState(
    val fase: Fase = Fase.Cargando,
    val busqueda: String = "",
    val soloPendientes: Boolean = false,
    val pendientesSincronizar: Long = 0L,
    val estadoRuta: String = "ABIERTA",
    val ultimaSincronizacion: String? = null,
    val sincronizando: Boolean = false,
    val resumenSincronizacion: ResumenSincronizacion? = null,
    val mensajeBanner: String? = null,
    val esErrorBanner: Boolean = false,
    val sesionExpirada401: Boolean = false
) {
    sealed interface Fase {
        data object Cargando : Fase
        data object ListaVacia : Fase
        data class Contenido(val puntos: List<PuntoAcopio>) : Fase
        data class ErrorServidor(val mensaje: String) : Fase
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListadoRutaViewModel(
    private val observarRutaUseCase: ObservarRutaUseCase,
    private val sincronizarUseCase: SincronizarUseCase,
    private val cerrarRutaUseCase: CerrarRutaUseCase
) : ViewModel() {

    private val _busqueda = MutableStateFlow("")
    private val _soloPendientes = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(ListadoRutaUiState())
    val uiState: StateFlow<ListadoRutaUiState> = _uiState.asStateFlow()

    init {
        observarBaseLocal()
        sincronizarAutomaticamente()
    }

    private fun observarBaseLocal() {
        // 1. Observa los puntos de la ruta filtrados en SQLDelight (SC-A)
        viewModelScope.launch {
            combine(_busqueda, _soloPendientes) { q, soloPend -> q to soloPend }
                .flatMapLatest { (q, soloPend) ->
                    observarRutaUseCase(busqueda = q, soloPendientes = soloPend)
                }
                .collect { puntos ->
                    _uiState.update { actual ->
                        val nuevaFase = if (puntos.isEmpty()) {
                            if (actual.sincronizando && actual.ultimaSincronizacion == null) {
                                ListadoRutaUiState.Fase.Cargando
                            } else {
                                ListadoRutaUiState.Fase.ListaVacia
                            }
                        } else {
                            ListadoRutaUiState.Fase.Contenido(puntos)
                        }
                        actual.copy(
                            fase = nuevaFase,
                            estadoRuta = observarRutaUseCase.estadoRuta(),
                            ultimaSincronizacion = observarRutaUseCase.ultimaSincronizacion()
                        )
                    }
                }
        }

        // 2. SC-D: Observa reactivamente desde SQLDelight el conteo de pesajes pendientes
        viewModelScope.launch {
            observarRutaUseCase.observarPendientesSincronizar()
                .collect { cantidad ->
                    _uiState.update { it.copy(pendientesSincronizar = cantidad) }
                }
        }
    }

    fun onBusquedaChange(nuevoTexto: String) {
        _busqueda.value = nuevoTexto
        _uiState.update { it.copy(busqueda = nuevoTexto) }
    }

    fun onToggleSoloPendientes() {
        val nuevoValor = !_soloPendientes.value
        _soloPendientes.value = nuevoValor
        _uiState.update { it.copy(soloPendientes = nuevoValor) }
    }

    /** RF-A06: Sincronizacion automatica al entrar o recuperar conexion */
    fun sincronizarAutomaticamente() {
        ejecutarSincronizacion(esManual = false)
    }

    /** RF-A06: Accion manual de sincronizar */
    fun sincronizarManual() {
        ejecutarSincronizacion(esManual = true)
    }

    private fun ejecutarSincronizacion(esManual: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    sincronizando = true,
                    mensajeBanner = null,
                    esErrorBanner = false
                )
            }

            sincronizarUseCase()
                .onSuccess { resumen ->
                    _uiState.update {
                        it.copy(
                            sincronizando = false,
                            resumenSincronizacion = resumen,
                            estadoRuta = resumen.estadoRuta,
                            ultimaSincronizacion = resumen.fechaHoraSincronizacion,
                            mensajeBanner = if (esManual) {
                                "Sincronización completada: ${resumen.enviados} enviado(s), ${resumen.puntosRecibidos} puntos y ${resumen.pesajesRecibidos} pesajes recibidos."
                            } else {
                                null
                            },
                            esErrorBanner = false
                        )
                    }
                }
                .onFailure { fallo ->
                    manejarError(fallo, esSincronizacion = true)
                }
        }
    }

    /** RF-B06: Cierre de ruta del dia (solo supervisor; verifica RN-04 en el servidor) */
    fun cerrarRutaDelDia() {
        viewModelScope.launch {
            _uiState.update { it.copy(sincronizando = true, mensajeBanner = null) }
            cerrarRutaUseCase()
                .onSuccess { nuevoEstado ->
                    _uiState.update {
                        it.copy(
                            sincronizando = false,
                            estadoRuta = nuevoEstado,
                            mensajeBanner = "Ruta del día cerrada correctamente.",
                            esErrorBanner = false
                        )
                    }
                }
                .onFailure { fallo ->
                    manejarError(fallo, esSincronizacion = false)
                }
        }
    }

    fun limpiarBanner() {
        _uiState.update { it.copy(mensajeBanner = null) }
    }

    private fun manejarError(fallo: Throwable, esSincronizacion: Boolean) {
        when (val error = fallo as? ErrorApi) {
            is ErrorApi.NoAutorizado -> _uiState.update {
                it.copy(
                    sincronizando = false,
                    sesionExpirada401 = true
                )
            }
            is ErrorApi.SinPrivilegio -> _uiState.update {
                // 403 Forbidden: se informa que la operacion no esta autorizada y la sesion SE MANTIENE
                it.copy(
                    sincronizando = false,
                    mensajeBanner = "403 No autorizado: ${error.detalle}",
                    esErrorBanner = true
                )
            }
            is ErrorApi.Conflicto -> _uiState.update {
                // 409 Conflict: se muestra el motivo devuelto por el servidor (ej. RN-04 puntos faltantes)
                it.copy(
                    sincronizando = false,
                    mensajeBanner = error.motivo,
                    esErrorBanner = true
                )
            }
            is ErrorApi.NoEncontrado -> {
                _uiState.update {
                    it.copy(
                        sincronizando = false,
                        mensajeBanner = error.detalle,
                        esErrorBanner = true
                    )
                }
            }
            is ErrorApi.Servidor -> _uiState.update {
                it.copy(
                    sincronizando = false,
                    fase = if (it.fase is ListadoRutaUiState.Fase.Contenido) it.fase else ListadoRutaUiState.Fase.ErrorServidor(error.detalle),
                    mensajeBanner = "${error.detalle} (Use el botón Sincronizar para reintentar)",
                    esErrorBanner = true
                )
            }
            is ErrorApi.SinConexion, is ErrorApi.TiempoAgotado -> _uiState.update {
                it.copy(
                    sincronizando = false,
                    mensajeBanner = if (esSincronizacion) {
                        "Modo sin conexión (Offline): mostrando datos de la base local SQLDelight."
                    } else {
                        error.message
                    },
                    esErrorBanner = false
                )
            }
            else -> _uiState.update {
                it.copy(
                    sincronizando = false,
                    mensajeBanner = fallo.message ?: "Error al procesar la operación",
                    esErrorBanner = true
                )
            }
        }
    }
}
