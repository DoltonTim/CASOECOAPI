package pe.upeu.ecorecicla.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import pe.upeu.ecorecicla.data.local.AlmacenSeguro

/**
 * ============================================================================
 * FABRICA DEL UNICO CLIENTE HTTP KTOR INYECTADO CON KOIN (SECCION 4.4)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Pregunta 4):
 * - ¿Donde se anade la cabecera de autorizacion?: En un UNICO punto centralizado:
 *   el bloque `install(Auth) { bearer { loadTokens { ... } } }` de este archivo.
 * - ¿Que ocurriria si lo hicieras funcion por funcion?:
 *   1. Se duplicaria la lectura del token en cada endpoint (listar, registrar, editar, anular, cerrar).
 *   2. Seria propenso a olvidar la cabecera en un endpoint nuevo (produciendo 401 inesperados).
 *   3. No se limpiaria ni refrescaria el cache de credenciales en un solo lugar al cerrar sesion.
 */
fun crearHttpClient(
    motor: HttpClientEngine,
    almacenSeguro: AlmacenSeguro,
    esDepuracion: Boolean = false
): HttpClient = HttpClient(motor) {
    expectSuccess = true

    // 1. Negociacion de contenido JSON con tolerancia a claves desconocidas
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                isLenient = true
            }
        )
    }

    // 2. Direccion base centralizada en ApiConfig.URL_BASE
    defaultRequest {
        url(ApiConfig.URL_BASE)
        contentType(ContentType.Application.Json)
    }

    // 3. Tiempos de espera configurados (HttpTimeout)
    install(HttpTimeout) {
        requestTimeoutMillis = ApiConfig.TIMEOUT_PETICION_MS
        connectTimeoutMillis = ApiConfig.TIMEOUT_CONEXION_MS
        socketTimeoutMillis = ApiConfig.TIMEOUT_SOCKET_MS
    }

    // 4. Punto UNICO donde se anade la cabecera Authorization: Bearer <token>
    install(Auth) {
        bearer {
            loadTokens {
                almacenSeguro.leerToken()?.takeIf { it.isNotBlank() }?.let { token ->
                    BearerTokens(accessToken = token, refreshToken = "")
                }
            }
            sendWithoutRequest { peticion ->
                !peticion.url.encodedPath.endsWith("/auth/login")
            }
        }
    }

    // 5. Registro reducido en produccion y ocultando la cabecera Authorization
    install(Logging) {
        logger = Logger.SIMPLE
        level = if (esDepuracion) LogLevel.HEADERS else LogLevel.NONE
        sanitizeHeader { nombre -> nombre == HttpHeaders.Authorization }
    }
}
