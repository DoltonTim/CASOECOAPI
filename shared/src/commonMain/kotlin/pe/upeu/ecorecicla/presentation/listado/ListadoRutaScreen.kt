package pe.upeu.ecorecicla.presentation.listado

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Sesion

/**
 * ============================================================================
 * PANTALLA PRINCIPAL DE RUTA DEL DIA (RF-A03, RF-A06 + SC-A Y SC-D)
 * ============================================================================
 * - Muestra los puntos de la ruta en orden de visita (`secuencia`) desde SQLDelight.
 * - Distingue visualmente los puntos ya pesados (`ATENDIDO`) de los `PENDIENTE DE PESAR`.
 * - SC-A: Incluye buscador y chip «Solo pendientes de pesar» resuelto en consulta local.
 * - SC-D: Muestra distintivo (`Badge`) con el conteo observable de pesajes pendientes de sincronizar.
 * - RF-B06: Ofrece «Cerrar ruta del día» solo si la sesion posee el privilegio `RUTA_CERRAR`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListadoRutaScreen(
    sesion: Sesion,
    uiState: ListadoRutaUiState,
    onBusquedaChange: (String) -> Unit,
    onToggleSoloPendientes: () -> Unit,
    onSincronizar: () -> Unit,
    onCerrarRuta: () -> Unit,
    onSeleccionarPunto: (Long) -> Unit,
    onIrAPerfil: () -> Unit
) {
    val puedeCerrarRuta = sesion.tienePrivilegio("RUTA_CERRAR")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Ruta del Día (${uiState.estadoRuta})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // SC-D: Distintivo con el numero de pesajes pendientes de sincronizar
                            Badge(
                                containerColor = if (uiState.pendientesSincronizar > 0) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                }
                            ) {
                                Text(
                                    text = "${uiState.pendientesSincronizar} pend. sync",
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = "${sesion.usuario} (${sesion.rol}) · ${uiState.ultimaSincronizacion ?: "Sin sincronizar aún"}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSincronizar,
                        enabled = !uiState.sincronizando
                    ) {
                        Text("Sincronizar", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    TextButton(onClick = onIrAPerfil) {
                        Text("Perfil", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            if (uiState.sincronizando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Resumen de sincronizacion o mensaje de regla de negocio / 403 / 409
            if (uiState.mensajeBanner != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.esErrorBanner) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    )
                ) {
                    Text(
                        text = uiState.mensajeBanner,
                        color = if (uiState.esErrorBanner) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // SC-A: Buscador combinado con el filtro «Solo pendientes de pesar»
            OutlinedTextField(
                value = uiState.busqueda,
                onValueChange = onBusquedaChange,
                label = { Text("Buscar por razón social, dirección, zona o material") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.soloPendientes,
                    onClick = onToggleSoloPendientes,
                    label = { Text("Solo pendientes de pesar (SC-A)") }
                )

                // El boton Cerrar Ruta solo se ofrece a quien posee el privilegio RUTA_CERRAR
                if (puedeCerrarRuta && uiState.estadoRuta != "CERRADA") {
                    Button(
                        onClick = onCerrarRuta,
                        enabled = !uiState.sincronizando
                    ) {
                        Text("Cerrar ruta")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Estados de interfaz exigidos en Seccion 3.5: Carga, Lista vacia, Error y Contenido
            when (val fase = uiState.fase) {
                is ListadoRutaUiState.Fase.Cargando -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Cargando ruta del día...")
                        }
                    }
                }
                is ListadoRutaUiState.Fase.ListaVacia -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (uiState.soloPendientes || uiState.busqueda.isNotBlank()) {
                                    "No hay puntos que coincidan con el filtro aplicado."
                                } else {
                                    "No hay puntos de acopio en la base local."
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = onSincronizar) {
                                Text("Sincronizar ahora")
                            }
                        }
                    }
                }
                is ListadoRutaUiState.Fase.ErrorServidor -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = fase.mensaje,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onSincronizar) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
                is ListadoRutaUiState.Fase.Contenido -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(fase.puntos, key = { it.id }) { punto ->
                            TarjetaPuntoRuta(
                                punto = punto,
                                onClick = { onSeleccionarPunto(punto.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaPuntoRuta(
    punto: PuntoAcopio,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (punto.atendido) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${punto.secuencia} · ${punto.razonSocial}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                AssistChip(
                    onClick = onClick,
                    label = {
                        Text(
                            text = if (punto.atendido) "PESADO" else "PENDIENTE",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Dirección: ${punto.direccion}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Zona: ${punto.zona} · Material habitual: ${punto.materialHabitual}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
