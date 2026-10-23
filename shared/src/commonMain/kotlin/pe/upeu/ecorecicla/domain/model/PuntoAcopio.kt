package pe.upeu.ecorecicla.domain.model

/**
 * Modelo de dominio de un Punto de Acopio en la ruta del dia (Seccion 4.5).
 * No contiene ninguna anotacion de serializacion ni dependencia de red o base de datos.
 */
data class PuntoAcopio(
    val id: Long,
    val razonSocial: String,
    val direccion: String,
    val zona: String,
    val materialHabitual: String,
    val secuencia: Int, // orden de visita en la ruta
    val atendido: Boolean
)
