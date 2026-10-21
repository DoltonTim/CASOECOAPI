package pe.upeu.ecorecicla.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.remote.EcoApiService
import pe.upeu.ecorecicla.data.remote.dto.LoginRequestDto
import pe.upeu.ecorecicla.data.remote.ejecutarLlamadaRemota
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.repository.AuthRepository

/**
 * ============================================================================
 * REPOSITORIO DE AUTENTICACION Y SESION PERSISTENTE (RF-A01, RF-A02 Y RF-A07)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Preguntas 2, 3 y 5):
 * - Al iniciar sesion: envia usuario y contrasena a `/api/v1/auth/login`, guarda el
 *   token en [AlmacenSeguro] (`EncryptedSharedPreferences`) y limpia el cache del
 *   `BearerAuthProvider` para que las siguientes peticiones usen el nuevo token.
 * - Al reabrir la aplicacion (`recuperarSesion`):
 *   1. Si no hay token guardado, devuelve `null` (va al inicio de sesion).
 *   2. Si hay token, invoca `/api/v1/auth/me` para verificar su vigencia:
 *      - Si sigue vigente (200 OK), devuelve la [Sesion] actualizada con sus privilegios.
 *      - Si vencio o fue alterado (`401 NoAutorizado`), borra el token y vuelve al inicio de sesion.
 *      - Si el operario reabre la app en un sotano sin cobertura (`SinConexion`), recupera
 *        la sesion cacheada en el almacenamiento seguro para poder seguir trabajando offline.
 * - Al cerrar sesion (`cerrarSesion`): borra el token y la sesion del almacenamiento seguro
 *   dejando el telefono limpio para el turno siguiente (RF-A02 y RF-A07).
 */
class AuthRepositoryImpl(
    private val api: EcoApiService,
    private val httpClient: HttpClient,
    private val almacenSeguro: AlmacenSeguro
) : AuthRepository {

    override suspend fun iniciarSesion(usuario: String, clave: String): Result<Sesion> =
        ejecutarLlamadaRemota(almacenSeguro) {
            val dto = api.login(LoginRequestDto(username = usuario, password = clave))
            val sesion = dto.aDominio()
            almacenSeguro.guardarToken(sesion.token)
            almacenSeguro.guardarSesionCache(sesion)
            httpClient.authProvider<BearerAuthProvider>()?.clearToken()
            sesion
        }

    override suspend fun recuperarSesion(): Result<Sesion?> {
        val tokenGuardado = almacenSeguro.leerToken()
        if (tokenGuardado.isNullOrBlank()) {
            return Result.success(null)
        }

        val resultadoRemoto = ejecutarLlamadaRemota(almacenSeguro) {
            val actualDto = api.usuarioActual()
            val sesion = actualDto.aDominio(tokenGuardado)
            almacenSeguro.guardarSesionCache(sesion)
            sesion
        }

        return resultadoRemoto.fold(
            onSuccess = { sesion -> Result.success(sesion) },
            onFailure = { error ->
                when (error) {
                    is ErrorApi.NoAutorizado -> {
                        // El token vencio o es invalido: se limpia y se pide iniciar sesion
                        almacenSeguro.borrarSesion()
                        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
                        Result.success(null)
                    }
                    is ErrorApi.SinConexion, is ErrorApi.TiempoAgotado -> {
                        // En un sotano sin cobertura, si hay sesion guardada se mantiene operativa
                        Result.success(almacenSeguro.leerSesionCache())
                    }
                    else -> Result.success(almacenSeguro.leerSesionCache())
                }
            }
        )
    }

    override suspend fun cerrarSesion() {
        almacenSeguro.borrarSesion()
        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
    }

    override fun sesionActual(): Sesion? = almacenSeguro.leerSesionCache()
}
