package pe.upeu.ecorecicla.presentation.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.Sesion

/**
 * ============================================================================
 * PANTALLA DE DETALLE DE PUNTO Y GESTION DE PESAJES (RF-A04, RF-A05 Y SC-C)
 * ============================================================================
 * - Las acciones «Registrar pesaje», «Corregir» y «Anular» se muestran segun los
 *   privilegios del usuario (`PESAJE_CREAR`, `PESAJE_EDITAR`, `PESAJE_ANULAR`).
 * - SC-C: Muestra y permite capturar la «Humedad estimada (%)» tanto en el formulario
 *   como en cada tarjeta del listado de pesajes del punto.
 * - Muestra debajo de cada campo los mensajes `validationErrors` devueltos por el servidor (400).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetallePuntoScreen(
    sesion: Sesion,
    uiState: DetallePuntoUiState,
    onVolver: () -> Unit,
    onAbrirNuevo: () -> Unit,
    onAbrirEdicion: (Pesaje) -> Unit,
    onCerrarFormulario: () -> Unit,
    onMaterialChange: (String) -> Unit,
    onPesoBrutoChange: (String) -> Unit,
    onTaraChange: (String) -> Unit,
    onHumedadChange: (String) -> Unit,
    onObservacionChange: (String) -> Unit,
    onGuardarPesaje: () -> Unit,
    onSolicitarAnulacion: (Pesaje) -> Unit,
    onMotivoAnulacionChange: (String) -> Unit,
    onConfirmarAnulacion: () -> Unit,
    onCancelarAnulacion: () -> Unit
) {
    val puedeCrear = sesion.tienePrivilegio("PESAJE_CREAR")
    val puedeEditar = sesion.tienePrivilegio("PESAJE_EDITAR")
    val puedeAnular = sesion.tienePrivilegio("PESAJE_ANULAR")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.punto?.razonSocial ?: "Detalle del Punto",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.punto?.direccion ?: ""} · Zona ${uiState.punto?.zona ?: ""}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onVolver) {
                        Text("← Volver", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    if (puedeCrear && !uiState.mostrarFormulario) {
                        TextButton(onClick = onAbrirNuevo) {
                            Text("+ Registrar pesaje", color = MaterialTheme.colorScheme.onPrimary)
                        }
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
            // Mensajes de exito o error de negocio (403, 409, etc.)
            if (uiState.mensajeExito != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = uiState.mensajeExito,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.mensajeError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.mensajeError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Formulario de registro o correccion de pesaje (RF-A05 + SC-C humedadEstimada)
            if (uiState.mostrarFormulario) {
                FormularioPesajeCard(
                    formulario = uiState.formulario,
                    guardando = uiState.guardando,
                    onMaterialChange = onMaterialChange,
                    onPesoBrutoChange = onPesoBrutoChange,
                    onTaraChange = onTaraChange,
                    onHumedadChange = onHumedadChange,
                    onObservacionChange = onObservacionChange,
                    onGuardar = onGuardarPesaje,
                    onCancelar = onCerrarFormulario
                )
                Spacer(modifier = Modifier.height(10.dp))
            } else if (puedeCrear) {
                Button(
                    onClick = onAbrirNuevo,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Registrar pesaje en este punto")
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Listado de pesajes del punto
            when (val fase = uiState.fase) {
                is DetallePuntoUiState.Fase.Cargando -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is DetallePuntoUiState.Fase.SinPesajes -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Este punto aún no tiene pesajes registrados.")
                    }
                }
                is DetallePuntoUiState.Fase.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(fase.mensaje, color = MaterialTheme.colorScheme.error)
                    }
                }
                is DetallePuntoUiState.Fase.ConPesajes -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(fase.pesajes, key = { it.id }) { pesaje ->
                            TarjetaPesaje(
                                pesaje = pesaje,
                                puedeEditar = puedeEditar,
                                puedeAnular = puedeAnular,
                                onEditar = { onAbrirEdicion(pesaje) },
                                onAnular = { onSolicitarAnulacion(pesaje) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogo de anulacion (visible solo para quien tiene PESAJE_ANULAR)
    if (uiState.pesajeParaAnular != null) {
        AlertDialog(
            onDismissRequest = onCancelarAnulacion,
            title = { Text("Anular pesaje #${uiState.pesajeParaAnular.id}") },
            text = {
                Column {
                    Text("La anulación no elimina el registro: lo marca como ANULADO con su motivo.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.motivoAnulacion,
                        onValueChange = onMotivoAnulacionChange,
                        label = { Text("Motivo de anulación") },
                        isError = uiState.motivoAnulacionError != null,
                        supportingText = {
                            uiState.motivoAnulacionError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmarAnulacion,
                    enabled = !uiState.guardando
                ) {
                    Text("Confirmar anulación")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelarAnulacion) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun FormularioPesajeCard(
    formulario: FormularioPesajeState,
    guardando: Boolean,
    onMaterialChange: (String) -> Unit,
    onPesoBrutoChange: (String) -> Unit,
    onTaraChange: (String) -> Unit,
    onHumedadChange: (String) -> Unit,
    onObservacionChange: (String) -> Unit,
    onGuardar: () -> Unit,
    onCancelar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (formulario.idEdicion == null) {
                    "Nuevo Pesaje (funciona con y sin conexión)"
                } else {
                    "Corregir Pesaje #${formulario.idEdicion}"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = formulario.material,
                onValueChange = onMaterialChange,
                label = { Text("Tipo de material") },
                singleLine = true,
                isError = formulario.materialError != null,
                supportingText = {
                    formulario.materialError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = formulario.pesoBruto,
                    onValueChange = onPesoBrutoChange,
                    label = { Text("Peso bruto (kg)") },
                    singleLine = true,
                    isError = formulario.pesoBrutoError != null,
                    supportingText = {
                        formulario.pesoBrutoError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = formulario.tara,
                    onValueChange = onTaraChange,
                    label = { Text("Tara (kg)") },
                    singleLine = true,
                    isError = formulario.taraError != null,
                    supportingText = {
                        formulario.taraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // SC-C: Campo «humedad estimada» en el formulario de pesaje
            OutlinedTextField(
                value = formulario.humedadEstimada,
                onValueChange = onHumedadChange,
                label = { Text("Humedad estimada (%) · SC-C") },
                singleLine = true,
                isError = formulario.humedadError != null,
                supportingText = {
                    formulario.humedadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = formulario.observacion,
                onValueChange = onObservacionChange,
                label = { Text("Observación (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onCancelar, enabled = !guardando) {
                    Text("Cancelar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onGuardar, enabled = !guardando) {
                    Text(if (guardando) "Guardando..." else "Guardar pesaje")
                }
            }
        }
    }
}

@Composable
private fun TarjetaPesaje(
    pesaje: Pesaje,
    puedeEditar: Boolean,
    puedeAnular: Boolean,
    onEditar: () -> Unit,
    onAnular: () -> Unit
) {
    val esAnulado = pesaje.estado is EstadoPesaje.Anulado
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                pesaje.pendiente -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                esAnulado -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pesaje.material} · Neto: ${pesaje.pesoNeto} kg",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = when {
                                pesaje.pendiente -> "PENDIENTE (ID ${pesaje.id})"
                                esAnulado -> "ANULADO"
                                else -> "REGISTRADO (#${pesaje.id})"
                            }
                        )
                    }
                )
            }

            // SC-C: Muestra tambien la humedad estimada en la tarjeta de detalle
            Text(
                text = "Bruto: ${pesaje.pesoBruto} kg · Tara: ${pesaje.tara} kg · Humedad: ${pesaje.humedadEstimada}%",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Operario: ${pesaje.operario} · Fecha: ${pesaje.fechaHora}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!pesaje.observacion.isNullOrBlank()) {
                Text(
                    text = "Obs: ${pesaje.observacion}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (pesaje.estado is EstadoPesaje.Anulado) {
                Text(
                    text = "Motivo de anulación: ${pesaje.estado.motivo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!esAnulado && (puedeEditar || puedeAnular)) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (puedeEditar) {
                        OutlinedButton(onClick = onEditar) {
                            Text("Corregir")
                        }
                    }
                    // RF-A04: La accion «Anular» solo es visible para quien tiene el privilegio PESAJE_ANULAR
                    if (puedeAnular && !pesaje.pendiente) {
                        OutlinedButton(onClick = onAnular) {
                            Text("Anular", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
