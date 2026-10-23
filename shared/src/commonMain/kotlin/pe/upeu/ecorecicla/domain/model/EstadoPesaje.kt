package pe.upeu.ecorecicla.domain.model

/**
 * Estado sellado de un pesaje en el dominio (Seccion 4.5).
 * La anulacion no borra el registro: lo marca con su motivo.
 */
sealed interface EstadoPesaje {
    data object Registrado : EstadoPesaje
    data class Anulado(val motivo: String) : EstadoPesaje
}
