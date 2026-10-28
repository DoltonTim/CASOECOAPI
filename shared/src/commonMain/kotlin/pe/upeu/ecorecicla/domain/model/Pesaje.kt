package pe.upeu.ecorecicla.domain.model

/**
 * Modelo de dominio de un Pesaje (Seccion 4.5 + SC-C humedadEstimada).
 * - `id`: negativo (-1, -2, ...) mientras este pendiente de envio al servidor.
 * - `pesoNeto`: lo calcula el servicio como pesoBruto - tara (offline se precalcula localmente
 *   y al sincronizar se reemplaza por el valor definitivo del servidor).
 * - `humedadEstimada`: porcentaje de humedad estimada del material reciclado (SC-C).
 */
data class Pesaje(
    val id: Long, // negativo mientras esté pendiente de envío
    val puntoId: Long,
    val fechaHora: String, // ISO-8601
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val pesoNeto: Double, // lo calcula el servicio
    val operario: String,
    val estado: EstadoPesaje,
    val pendiente: Boolean,
    val observacion: String? = null,
    val humedadEstimada: Double = 0.0
)
