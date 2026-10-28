package pe.upeu.ecorecicla.domain.model

/**
 * Modelo de dominio de la Sesion autenticada del usuario (Seccion 4.5).
 * Contiene el token JWT, el usuario, su rol y el conjunto de privilegios
 * que determinan que acciones ofrece la interfaz (Seccion 3.3).
 */
data class Sesion(
    val token: String,
    val usuario: String,
    val rol: String,
    val privilegios: Set<String>,
    val nombreCompleto: String = usuario
) {
    fun tienePrivilegio(privilegio: String): Boolean = privilegios.contains(privilegio)
}

/**
 * Resumen devuelto tras ejecutar una sincronizacion offline-first (RF-A06).
 */
data class ResumenSincronizacion(
    val enviados: Int,
    val puntosRecibidos: Int,
    val pesajesRecibidos: Int,
    val estadoRuta: String,
    val fechaHoraSincronizacion: String
)
