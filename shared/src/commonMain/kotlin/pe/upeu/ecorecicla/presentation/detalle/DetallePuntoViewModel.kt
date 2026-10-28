package pe.upeu.ecorecicla.presentation.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.usecase.AnularPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.CorregirPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarPesajesPuntoUseCase
import pe.upeu.ecorecicla.domain.usecase.RegistrarPesajeUseCase

/**
 * ============================================================================
 * ESTADO Y VIEWMODEL DE DETALLE DE PUNTO Y FORMULARIO DE PESAJE (RF-A04, RF-A05, SC-C)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Preguntas 5, 8 y 11):
 * - Pregunta 11 (Camino del error 400 hasta el campo del formulario):
 *   1. El servidor rechaza con `400 Bad Request` y devuelve `validationErrors: { "tara": "..." }`.
 *   2. `MapeoErrores.kt` lo traduce a `ErrorApi.Validacion(porCampo)`.
 *   3. Aqui en `manejarFalloFormulario` se asigna `taraError = error.porCampo["tara"]`,
 *      `pesoBrutoError = error.porCampo["pesoBruto"]`, `materialError = error.porCampo["material"]`
 *      y `humedadError = error.porCampo["humedadEstimada"]`.
 *   4. `DetallePuntoScreen` pinta ese mensaje en rojo en el `supportingText` debajo del `OutlinedTextField`.
 */
data class FormularioPesajeState(
    val idEdicion: Long? = null,
    val material: String = "",
    val pesoBruto: String = "",
    val tara: String = "",
    val humedadEstimada: String = "0.0",
    val observacion: String = "",
    val materialError: String? = null,
    val pesoBrutoError: String? = null,
    val taraError: String? = null,
    val humedadError: String? = null
)

data class DetallePuntoUiState(
    val punto: PuntoAcopio? = null,
    val fase: Fase = Fase.Cargando,
    val mostrarFormulario: Boolean = false,
    val formulario: FormularioPesajeState = FormularioPesajeState(),
    val guardando: Boolean = false,
    val pesajeParaAnular: Pesaje? = null,
    val motivoAnulacion: String = "",
    val motivoAnulacionError: String? = null,
    val mensajeExito: String? = null,
    val mensajeError: String? = null,
    val sesionExpirada401: Boolean = false
) {
    sealed interface Fase {
        data object Cargando : Fase
        data object SinPesajes : Fase
        data class ConPesajes(val pesajes: List<Pesaje>) : Fase
        data class Error(val mensaje: String) : Fase
    }
}

class DetallePuntoViewModel(
    private val observarPesajesPuntoUseCase: ObservarPesajesPuntoUseCase,
    private val registrarPesajeUseCase: RegistrarPesajeUseCase,
    private val corregirPesajeUseCase: CorregirPesajeUseCase,
    private val anularPesajeUseCase: AnularPesajeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetallePuntoUiState())
    val uiState: StateFlow<DetallePuntoUiState> = _uiState.asStateFlow()

    private var observacionJob: Job? = null
    private var puntoIdActual: Long = 0L

    fun cargarPunto(puntoId: Long) {
        puntoIdActual = puntoId
        val puntoLocal = observarPesajesPuntoUseCase.obtenerPunto(puntoId)
        _uiState.update {
            it.copy(
                punto = puntoLocal,
                fase = DetallePuntoUiState.Fase.Cargando,
                mostrarFormulario = false,
                pesajeParaAnular = null,
                mensajeExito = null,
                mensajeError = null,
                formulario = FormularioPesajeState(
                    material = puntoLocal?.materialHabitual ?: ""
                )
            )
        }

        observacionJob?.cancel()
        observacionJob = viewModelScope.launch {
            observarPesajesPuntoUseCase(puntoId).collect { lista ->
                _uiState.update { actual ->
                    actual.copy(
                        punto = observarPesajesPuntoUseCase.obtenerPunto(puntoId) ?: actual.punto,
                        fase = if (lista.isEmpty()) {
                            DetallePuntoUiState.Fase.SinPesajes
                        } else {
                            DetallePuntoUiState.Fase.ConPesajes(lista)
                        }
                    )
                }
            }
        }

        refrescarDesdeServidor()
    }

    fun refrescarDesdeServidor() {
        val id = puntoIdActual
        if (id <= 0L) return
        viewModelScope.launch {
            observarPesajesPuntoUseCase.refrescar(id)
                .onFailure { fallo ->
                    if (fallo is ErrorApi.NoAutorizado) {
                        _uiState.update { it.copy(sesionExpirada401 = true) }
                    }
                }
        }
    }

    fun abrirFormularioNuevo() {
        val materialPorDefecto = _uiState.value.punto?.materialHabitual ?: ""
        _uiState.update {
            it.copy(
                mostrarFormulario = true,
                mensajeError = null,
                mensajeExito = null,
                formulario = FormularioPesajeState(
                    idEdicion = null,
                    material = materialPorDefecto,
                    pesoBruto = "",
                    tara = "",
                    humedadEstimada = "3.0",
                    observacion = ""
                )
            )
        }
    }

    fun abrirFormularioEdicion(pesaje: Pesaje) {
        _uiState.update {
            it.copy(
                mostrarFormulario = true,
                mensajeError = null,
                mensajeExito = null,
                formulario = FormularioPesajeState(
                    idEdicion = pesaje.id,
                    material = pesaje.material,
                    pesoBruto = pesaje.pesoBruto.toString(),
                    tara = pesaje.tara.toString(),
                    humedadEstimada = pesaje.humedadEstimada.toString(),
                    observacion = pesaje.observacion ?: ""
                )
            )
        }
    }

    fun cerrarFormulario() {
        _uiState.update { it.copy(mostrarFormulario = false) }
    }

    fun onMaterialChange(v: String) = _uiState.update {
        it.copy(formulario = it.formulario.copy(material = v, materialError = null))
    }

    fun onPesoBrutoChange(v: String) = _uiState.update {
        it.copy(formulario = it.formulario.copy(pesoBruto = v, pesoBrutoError = null))
    }

    fun onTaraChange(v: String) = _uiState.update {
        it.copy(formulario = it.formulario.copy(tara = v, taraError = null))
    }

    fun onHumedadChange(v: String) = _uiState.update {
        it.copy(formulario = it.formulario.copy(humedadEstimada = v, humedadError = null))
    }

    fun onObservacionChange(v: String) = _uiState.update {
        it.copy(formulario = it.formulario.copy(observacion = v))
    }

    fun guardarPesaje() {
        val form = _uiState.value.formulario
        val bruto = form.pesoBruto.replace(',', '.').toDoubleOrNull()
        val tara = form.tara.replace(',', '.').toDoubleOrNull()
        val humedad = form.humedadEstimada.replace(',', '.').toDoubleOrNull() ?: 0.0

        // Validaciones basicas de formato numerico; permitimos enviar tara >= bruto al servidor
        // para poder demostrar en vivo el error 400 de RN-03 desde el backend (Pregunta 11).
        var hayErrorFormato = false
        if (form.material.isBlank()) {
            _uiState.update {
                it.copy(formulario = it.formulario.copy(materialError = "El tipo de material es obligatorio"))
            }
            hayErrorFormato = true
        }
        if (bruto == null || bruto <= 0.0) {
            _uiState.update {
                it.copy(formulario = it.formulario.copy(pesoBrutoError = "Ingrese un peso bruto numérico mayor a 0"))
            }
            hayErrorFormato = true
        }
        if (tara == null || tara < 0.0) {
            _uiState.update {
                it.copy(formulario = it.formulario.copy(taraError = "Ingrese una tara numérica válida (>= 0)"))
            }
            hayErrorFormato = true
        }
        if (humedad < 0.0 || humedad > 100.0) {
            _uiState.update {
                it.copy(formulario = it.formulario.copy(humedadError = "La humedad estimada debe estar entre 0 y 100%"))
            }
            hayErrorFormato = true
        }
        if (hayErrorFormato) return

        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true, mensajeError = null, mensajeExito = null) }

            val resultado = if (form.idEdicion == null) {
                registrarPesajeUseCase(
                    puntoId = puntoIdActual,
                    material = form.material,
                    pesoBruto = bruto!!,
                    tara = tara!!,
                    humedadEstimada = humedad,
                    observacion = form.observacion
                )
            } else {
                corregirPesajeUseCase(
                    id = form.idEdicion,
                    puntoId = puntoIdActual,
                    material = form.material,
                    pesoBruto = bruto!!,
                    tara = tara!!,
                    humedadEstimada = humedad,
                    observacion = form.observacion
                )
            }

            resultado
                .onSuccess { guardado ->
                    val aviso = if (guardado.pendiente) {
                        "Guardado sin conexión como PENDIENTE (ID provisional ${guardado.id}). Se enviará al sincronizar."
                    } else if (form.idEdicion == null) {
                        "Pesaje #${guardado.id} registrado en el servidor (Neto: ${guardado.pesoNeto} kg · Humedad: ${guardado.humedadEstimada}%)."
                    } else {
                        "Pesaje #${guardado.id} corregido correctamente (Neto: ${guardado.pesoNeto} kg)."
                    }
                    _uiState.update {
                        it.copy(
                            guardando = false,
                            mostrarFormulario = false,
                            mensajeExito = aviso,
                            mensajeError = null
                        )
                    }
                }
                .onFailure { fallo ->
                    manejarFalloFormulario(fallo)
                }
        }
    }

    fun solicitarAnulacion(pesaje: Pesaje) {
        _uiState.update {
            it.copy(
                pesajeParaAnular = pesaje,
                motivoAnulacion = "",
                motivoAnulacionError = null,
                mensajeError = null,
                mensajeExito = null
            )
        }
    }

    fun cancelarAnulacion() {
        _uiState.update { it.copy(pesajeParaAnular = null, motivoAnulacion = "", motivoAnulacionError = null) }
    }

    fun onMotivoAnulacionChange(valor: String) {
        _uiState.update { it.copy(motivoAnulacion = valor, motivoAnulacionError = null) }
    }

    fun confirmarAnulacion() {
        val pesaje = _uiState.value.pesajeParaAnular ?: return
        val motivo = _uiState.value.motivoAnulacion.trim()
        if (motivo.length < 4) {
            _uiState.update { it.copy(motivoAnulacionError = "Indique el motivo de anulación (mínimo 4 caracteres)") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true, mensajeError = null) }
            anularPesajeUseCase(id = pesaje.id, puntoId = puntoIdActual, motivo = motivo)
                .onSuccess { anulado ->
                    _uiState.update {
                        it.copy(
                            guardando = false,
                            pesajeParaAnular = null,
                            motivoAnulacion = "",
                            mensajeExito = "Pesaje #${anulado.id} marcado como ANULADO."
                        )
                    }
                }
                .onFailure { fallo ->
                    manejarFalloFormulario(fallo)
                }
        }
    }

    private fun manejarFalloFormulario(fallo: Throwable) {
        when (val error = fallo as? ErrorApi) {
            is ErrorApi.Validacion -> _uiState.update {
                // 400 Bad Request: el mensaje del servidor se coloca bajo el campo que lo origino
                it.copy(
                    guardando = false,
                    formulario = it.formulario.copy(
                        materialError = error.porCampo["material"],
                        pesoBrutoError = error.porCampo["pesoBruto"],
                        taraError = error.porCampo["tara"],
                        humedadError = error.porCampo["humedadEstimada"]
                    ),
                    motivoAnulacionError = error.porCampo["motivo"],
                    mensajeError = if (error.porCampo.isEmpty()) error.detalle else null
                )
            }
            is ErrorApi.NoAutorizado -> _uiState.update {
                // 401 Unauthorized: limpia la sesion y vuelve al inicio de sesion
                it.copy(guardando = false, sesionExpirada401 = true)
            }
            is ErrorApi.SinPrivilegio -> _uiState.update {
                // 403 Forbidden: informa que la operacion no esta autorizada y la sesion SE MANTIENE
                it.copy(
                    guardando = false,
                    pesajeParaAnular = null,
                    mensajeError = "403 Operación no autorizada: ${error.detalle}"
                )
            }
            is ErrorApi.Conflicto -> _uiState.update {
                // 409 Conflict (RN-01 / RN-04): muestra el motivo devuelto por el servidor
                it.copy(
                    guardando = false,
                    pesajeParaAnular = null,
                    mensajeError = error.motivo
                )
            }
            is ErrorApi.NoEncontrado -> {
                // 404 Not Found: se informa y se refresca el listado; no se ofrece reintentar
                _uiState.update {
                    it.copy(
                        guardando = false,
                        mostrarFormulario = false,
                        pesajeParaAnular = null,
                        mensajeError = error.detalle
                    )
                }
                refrescarDesdeServidor()
            }
            is ErrorApi.Servidor -> _uiState.update {
                it.copy(
                    guardando = false,
                    mensajeError = "${error.detalle} (Puede reintentar la operación)"
                )
            }
            else -> _uiState.update {
                it.copy(
                    guardando = false,
                    mensajeError = error?.message ?: fallo.message ?: "Error al procesar el pesaje"
                )
            }
        }
    }
}
