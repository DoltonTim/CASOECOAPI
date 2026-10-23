package pe.upeu.ecorecicla.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel
import pe.upeu.ecorecicla.presentation.detalle.DetallePuntoScreen
import pe.upeu.ecorecicla.presentation.detalle.DetallePuntoViewModel
import pe.upeu.ecorecicla.presentation.listado.ListadoRutaScreen
import pe.upeu.ecorecicla.presentation.listado.ListadoRutaViewModel
import pe.upeu.ecorecicla.presentation.perfil.PerfilScreen
import pe.upeu.ecorecicla.presentation.sesion.LoginScreen
import pe.upeu.ecorecicla.presentation.sesion.SesionViewModel

sealed interface RutaPantalla {
    data object ListadoRuta : RutaPantalla
    data class DetallePunto(val puntoId: Long) : RutaPantalla
    data object Perfil : RutaPantalla
}

@Composable
fun EcoNavigation() {
    val sesionViewModel: SesionViewModel = koinViewModel()
    val sesionState by sesionViewModel.uiState.collectAsState()

    var pantallaActual by remember { mutableStateOf<RutaPantalla>(RutaPantalla.ListadoRuta) }

    if (sesionState.verificandoSesionInicial) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val sesionActiva = sesionState.sesionActiva
    if (sesionActiva == null) {
        pantallaActual = RutaPantalla.ListadoRuta
        LoginScreen(
            uiState = sesionState,
            onUsuarioChange = sesionViewModel::onUsuarioChange,
            onClaveChange = sesionViewModel::onClaveChange,
            onIniciarSesion = sesionViewModel::iniciarSesion,
            onAutocompletar = sesionViewModel::autocompletarUsuarioPrueba
        )
        return
    }

    val listadoViewModel: ListadoRutaViewModel = koinViewModel()
    val listadoState by listadoViewModel.uiState.collectAsState()

    LaunchedEffect(listadoState.sesionExpirada401) {
        if (listadoState.sesionExpirada401) {
            sesionViewModel.cerrarSesion("Su sesión ha expirado (401). Inicie sesión nuevamente.")
        }
    }

    when (val destino = pantallaActual) {
        is RutaPantalla.ListadoRuta -> {
            ListadoRutaScreen(
                sesion = sesionActiva,
                uiState = listadoState,
                onBusquedaChange = listadoViewModel::onBusquedaChange,
                onToggleSoloPendientes = listadoViewModel::onToggleSoloPendientes,
                onSincronizar = listadoViewModel::sincronizarManual,
                onCerrarRuta = listadoViewModel::cerrarRutaDelDia,
                onSeleccionarPunto = { puntoId ->
                    pantallaActual = RutaPantalla.DetallePunto(puntoId)
                },
                onIrAPerfil = {
                    pantallaActual = RutaPantalla.Perfil
                }
            )
        }
        is RutaPantalla.DetallePunto -> {
            val detalleViewModel: DetallePuntoViewModel = koinViewModel()
            val detalleState by detalleViewModel.uiState.collectAsState()

            LaunchedEffect(destino.puntoId) {
                detalleViewModel.cargarPunto(destino.puntoId)
            }

            LaunchedEffect(detalleState.sesionExpirada401) {
                if (detalleState.sesionExpirada401) {
                    sesionViewModel.cerrarSesion("Su sesión ha expirado (401). Inicie sesión nuevamente.")
                }
            }

            DetallePuntoScreen(
                sesion = sesionActiva,
                uiState = detalleState,
                onVolver = {
                    pantallaActual = RutaPantalla.ListadoRuta
                },
                onAbrirNuevo = detalleViewModel::abrirFormularioNuevo,
                onAbrirEdicion = detalleViewModel::abrirFormularioEdicion,
                onCerrarFormulario = detalleViewModel::cerrarFormulario,
                onMaterialChange = detalleViewModel::onMaterialChange,
                onPesoBrutoChange = detalleViewModel::onPesoBrutoChange,
                onTaraChange = detalleViewModel::onTaraChange,
                onHumedadChange = detalleViewModel::onHumedadChange,
                onObservacionChange = detalleViewModel::onObservacionChange,
                onGuardarPesaje = detalleViewModel::guardarPesaje,
                onSolicitarAnulacion = detalleViewModel::solicitarAnulacion,
                onMotivoAnulacionChange = detalleViewModel::onMotivoAnulacionChange,
                onConfirmarAnulacion = detalleViewModel::confirmarAnulacion,
                onCancelarAnulacion = detalleViewModel::cancelarAnulacion
            )
        }
        is RutaPantalla.Perfil -> {
            PerfilScreen(
                sesion = sesionActiva,
                onVolver = {
                    pantallaActual = RutaPantalla.ListadoRuta
                },
                onCerrarSesion = {
                    sesionViewModel.cerrarSesion()
                }
            )
        }
    }
}
