package pe.upeu.ecorecicla.presentation.sesion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.usecase.CerrarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.IniciarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.RecuperarSesionUseCase

/**
 * ============================================================================
 * ESTADO Y VIEWMODEL DE SESION Y TURNO COMPARTIDO (RF-A01, RF-A02 Y RF-A07)
 * ============================================================================
 * EXPLICACION PARA DEFENSA:
 * - Al iniciar la app (`verificarSesionGuardada`), comprueba si existe un token
 *   vigente en `AlmacenSeguro`. Si sigue vigente, entra directo a la ruta del dia;
 *   si vencio (`401`), limpia el token y muestra el formulario de login.
 * - En `iniciarSesion`, distingue explicitamente entre:
 *   1. Credenciales incorrectas (`ErrorApi.NoAutorizado` -> mensaje de usuario/clave invalidos).
 *   2. Falta de conexion (`ErrorApi.SinConexion` / `TiempoAgotado` -> mensaje de red no disponible).
 *   3. Errores de validacion por campo (`ErrorApi.Validacion`).
 */
data class SesionUiState(
    val verificandoSesionInicial: Boolean = true,
    val usuario: String = "",
    val clave: String = "",
    val usuarioError: String? = null,
    val claveError: String? = null,
    val cargando: Boolean = false,
    val errorCredenciales: String? = null,
    val errorConexion: String? = null,
    val sesionActiva: Sesion? = null
)

class SesionViewModel(
    private val iniciarSesionUseCase: IniciarSesionUseCase,
    private val recuperarSesionUseCase: RecuperarSesionUseCase,
    private val cerrarSesionUseCase: CerrarSesionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SesionUiState())
    val uiState: StateFlow<SesionUiState> = _uiState.asStateFlow()

    init {
        verificarSesionGuardada()
    }

    fun verificarSesionGuardada() {
        viewModelScope.launch {
            _uiState.update { it.copy(verificandoSesionInicial = true) }
            recuperarSesionUseCase()
                .onSuccess { sesion ->
                    _uiState.update {
                        it.copy(
                            verificandoSesionInicial = false,
                            sesionActiva = sesion
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            verificandoSesionInicial = false,
                            sesionActiva = null
                        )
                    }
                }
        }
    }

    fun onUsuarioChange(valor: String) {
        _uiState.update {
            it.copy(
                usuario = valor,
                usuarioError = null,
                errorCredenciales = null,
                errorConexion = null
            )
        }
    }

    fun onClaveChange(valor: String) {
        _uiState.update {
            it.copy(
                clave = valor,
                claveError = null,
                errorCredenciales = null,
                errorConexion = null
            )
        }
    }

    fun autocompletarUsuarioPrueba(usuario: String, clave: String = "Eco2026*") {
        _uiState.update {
            it.copy(
                usuario = usuario,
                clave = clave,
                usuarioError = null,
                claveError = null,
                errorCredenciales = null,
                errorConexion = null
            )
        }
    }

    fun iniciarSesion() {
        val actual = _uiState.value
        if (actual.usuario.isBlank()) {
            _uiState.update { it.copy(usuarioError = "Ingrese su nombre de usuario") }
            return
        }
        if (actual.clave.isBlank()) {
            _uiState.update { it.copy(claveError = "Ingrese su contraseña") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    cargando = true,
                    usuarioError = null,
                    claveError = null,
                    errorCredenciales = null,
                    errorConexion = null
                )
            }

            iniciarSesionUseCase(actual.usuario, actual.clave)
                .onSuccess { sesion ->
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            clave = "",
                            sesionActiva = sesion
                        )
                    }
                }
                .onFailure { fallo ->
                    when (val error = fallo as? ErrorApi) {
                        is ErrorApi.Validacion -> _uiState.update {
                            it.copy(
                                cargando = false,
                                usuarioError = error.porCampo["username"],
                                claveError = error.porCampo["password"]
                            )
                        }
                        is ErrorApi.NoAutorizado -> _uiState.update {
                            it.copy(
                                cargando = false,
                                errorCredenciales = "Credenciales incorrectas: verifique su usuario y contraseña."
                            )
                        }
                        is ErrorApi.SinConexion, is ErrorApi.TiempoAgotado -> _uiState.update {
                            it.copy(
                                cargando = false,
                                errorConexion = "Falta de conexión: no se pudo contactar al servicio EcoAPI (verifique la red o el servidor)."
                            )
                        }
                        else -> _uiState.update {
                            it.copy(
                                cargando = false,
                                errorConexion = error?.message ?: "Error del servidor al iniciar sesión."
                            )
                        }
                    }
                }
        }
    }

    /** RF-A02 / RF-A07 / 401: Limpia el token y vuelve al inicio de sesion dejando limpio el turno */
    fun cerrarSesion(mensajeMotivo: String? = null) {
        viewModelScope.launch {
            cerrarSesionUseCase()
            _uiState.update {
                SesionUiState(
                    verificandoSesionInicial = false,
                    usuario = "",
                    clave = "",
                    sesionActiva = null,
                    errorCredenciales = mensajeMotivo
                )
            }
        }
    }
}
