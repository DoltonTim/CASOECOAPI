package pe.upeu.ecorecicla.domain.repository

import pe.upeu.ecorecicla.domain.model.Sesion

/**
 * Contrato del repositorio de autenticacion y sesion en el dominio (Seccion 4.7).
 */
interface AuthRepository {
    suspend fun iniciarSesion(usuario: String, clave: String): Result<Sesion>
    suspend fun recuperarSesion(): Result<Sesion?>
    suspend fun cerrarSesion()
    fun sesionActual(): Sesion?
}
