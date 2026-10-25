package pe.upeu.ecorecicla

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.local.BaseLocal
import pe.upeu.ecorecicla.data.remote.EcoApiService
import pe.upeu.ecorecicla.data.remote.crearHttpClient
import pe.upeu.ecorecicla.data.remote.dto.PesajeDto
import pe.upeu.ecorecicla.data.remote.ejecutarLlamadaRemota
import pe.upeu.ecorecicla.data.repository.EcoRepositoryOfflineImpl
import pe.upeu.ecorecicla.db.EcoReciclaDb
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Sesion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * ============================================================================
 * PRUEBAS AUTOMATIZADAS EN COMMONTEST (SECCION 4.4 Y PREGUNTA 10 DE DEFENSA)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Pregunta 10):
 * - ¿Que sustituimos para la base de datos?:
 *   Sustituimos `AndroidSqliteDriver` por `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)`
 *   creando el esquema real de `EcoReciclaDb` en memoria RAM para cada prueba.
 * - ¿Que sustituimos para la red?:
 *   Sustituimos `OkHttp` por `MockEngine` de Ktor (`io.ktor:ktor-client-mock`), que simula
 *   las respuestas HTTP (200, 201, 400, 401, 403, 409) o fallos de red sin tocar un servidor externo.
 * - ¿Que comportamiento garantizan?:
 *   1. Conversion de DTO al modelo de dominio (incluyendo `EstadoPesaje.Anulado` y `humedadEstimada`).
 *   2. Traduccion de un error HTTP 400 (`validationErrors`), 401 (`NoAutorizado` que borra el token)
 *      y 403 (`SinPrivilegio` que mantiene la sesion) a la jerarquia `ErrorApi`.
 *   3. Registro sin conexion (modo avion): asigna un ID local negativo (`< 0`), marca `pendiente = true`
 *      y actualiza el flujo observable de SQLDelight.
 *   4. Orden de la sincronizacion: primero sube lo pendiente (`POST /api/v1/pesajes`) y despues
 *      descarga lo actualizado (`GET /api/v1/rutas/hoy` y `GET /api/v1/puntos/{id}/pesajes`),
 *      sustituyendo el ID negativo por el definitivo positivo sin duplicar ni perder filas.
 */
class EcoReciclaCommonTest {

    private class AlmacenEnMemoria(tokenInicial: String? = "token-prueba-jwt") : AlmacenSeguro {
        private var token: String? = tokenInicial
        private var sesion: Sesion? = Sesion(
            token = tokenInicial ?: "",
            usuario = "operario1",
            rol = "OPERARIO",
            privilegios = setOf("PUNTO_LEER", "PESAJE_LEER", "PESAJE_CREAR", "PESAJE_EDITAR")
        )
        override fun guardarToken(token: String) { this.token = token }
        override fun leerToken(): String? = token
        override fun borrarToken() { this.token = null }
        override fun guardarSesionCache(sesion: Sesion) { this.sesion = sesion }
        override fun leerSesionCache(): Sesion? = sesion
        override fun borrarSesion() { token = null; sesion = null }
    }

    private fun crearBaseEnMemoria(): BaseLocal {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EcoReciclaDb.Schema.create(driver)
        return BaseLocal(EcoReciclaDb(driver))
    }

    /**
     * PRUEBA 1: Conversion de DTO al modelo de dominio (incluyendo estado anulado y humedadEstimada SC-C).
     */
    @Test
    fun `prueba 1 - conversion de PesajeDto al modelo de dominio respeta tipos, estado y humedad`() {
        val dtoRegistrado = PesajeDto(
            id = 15L,
            puntoId = 1L,
            fechaHora = "2026-10-28T10:15:00",
            material = "PET Cristal",
            pesoBruto = 125.5,
            tara = 15.5,
            pesoNeto = 110.0,
            humedadEstimada = 4.5,
            observacion = "Compactado",
            operario = "operario1",
            estado = "REGISTRADO",
            motivoAnulacion = null
        )
        val dominioRegistrado = dtoRegistrado.aDominio(pendiente = false)
        assertEquals(15L, dominioRegistrado.id)
        assertEquals(110.0, dominioRegistrado.pesoNeto)
        assertEquals(4.5, dominioRegistrado.humedadEstimada)
        assertIs<EstadoPesaje.Registrado>(dominioRegistrado.estado)
        assertFalse(dominioRegistrado.pendiente)

        val dtoAnulado = dtoRegistrado.copy(
            id = 16L,
            estado = "ANULADO",
            motivoAnulacion = "Balanza descalibrada"
        )
        val dominioAnulado = dtoAnulado.aDominio(pendiente = false)
        val estadoAnulado = assertIs<EstadoPesaje.Anulado>(dominioAnulado.estado)
        assertEquals("Balanza descalibrada", estadoAnulado.motivo)
    }

    /**
     * PRUEBA 2: Traduccion de un error 400 (validationErrors) y 401/403 con MockEngine de Ktor.
     */
    @Test
    fun `prueba 2 - traduccion de error 400 validationErrors y distincion entre 401 y 403 con MockEngine`() = runTest {
        val almacen = AlmacenEnMemoria("jwt-inicial")

        val motor400 = MockEngine {
            respond(
                content = """
                    {
                      "timestamp": "2026-10-28T10:00:00",
                      "status": 400,
                      "error": "Bad Request",
                      "message": "Datos invalidos",
                      "path": "/api/v1/pesajes",
                      "validationErrors": {
                        "tara": "RN-03: La tara debe ser menor al peso bruto"
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val api400 = EcoApiService(crearHttpClient(motor400, almacen, esDepuracion = false))
        val resultado400 = ejecutarLlamadaRemota(almacen) {
            api400.obtenerRutaHoy()
        }
        assertTrue(resultado400.isFailure)
        val errorValidacion = assertIs<ErrorApi.Validacion>(resultado400.exceptionOrNull())
        assertEquals("RN-03: La tara debe ser menor al peso bruto", errorValidacion.porCampo["tara"])

        // 403 Forbidden: NO borra el token de la sesion
        val motor403 = MockEngine {
            respond(
                content = """{"timestamp":"2026-10-28T10:00:00","status":403,"error":"Forbidden","message":"Sin privilegio RUTA_CERRAR","path":"/api/v1/rutas/hoy/cerrar"}""",
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val api403 = EcoApiService(crearHttpClient(motor403, almacen, esDepuracion = false))
        val resultado403 = ejecutarLlamadaRemota(almacen) { api403.cerrarRutaHoy() }
        assertIs<ErrorApi.SinPrivilegio>(resultado403.exceptionOrNull())
        assertEquals("jwt-inicial", almacen.leerToken(), "El 403 NO debe borrar el token de la sesion")

        // 401 Unauthorized: SI borra el token de AlmacenSeguro
        val motor401 = MockEngine {
            respond(
                content = """{"timestamp":"2026-10-28T10:00:00","status":401,"error":"Unauthorized","message":"Token vencido","path":"/api/v1/rutas/hoy"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val api401 = EcoApiService(crearHttpClient(motor401, almacen, esDepuracion = false))
        val resultado401 = ejecutarLlamadaRemota(almacen) { api401.obtenerRutaHoy() }
        assertIs<ErrorApi.NoAutorizado>(resultado401.exceptionOrNull())
        assertEquals(null, almacen.leerToken(), "El 401 debe borrar el token de AlmacenSeguro")
    }

    /**
     * PRUEBA 3: Registro sin conexion (modo avion) guarda en SQLDelight con ID negativo y marca pendiente.
     */
    @Test
    fun `prueba 3 - registro sin conexion guarda fila pendiente con id negativo y actualiza flujo observable`() = runTest {
        val baseLocal = crearBaseEnMemoria()
        val almacen = AlmacenEnMemoria()

        // Motor HTTP que simula falta de red (modo avion en sotano)
        val motorSinRed = MockEngine {
            throw IllegalStateException("Network unreachable - Modo avion activo")
        }
        val apiSinRed = EcoApiService(crearHttpClient(motorSinRed, almacen))
        val repo = EcoRepositoryOfflineImpl(baseLocal, apiSinRed, almacen)

        val resultado = repo.registrarPesaje(
            puntoId = 1L,
            material = "Carton Corrugado",
            pesoBruto = 85.0,
            tara = 5.0,
            humedadEstimada = 6.5,
            observacion = "Pesado en sotano sin senal"
        )

        assertTrue(resultado.isSuccess)
        val pesajeOffline = resultado.getOrThrow()
        assertTrue(pesajeOffline.id < 0L, "El ID provisional local debe ser negativo: ${pesajeOffline.id}")
        assertTrue(pesajeOffline.pendiente, "El registro sin red debe quedar marcado como pendiente")
        assertEquals(80.0, pesajeOffline.pesoNeto)
        assertEquals(6.5, pesajeOffline.humedadEstimada)

        // Verifica que el flujo observable de SQLDelight emite la fila y el contador SC-D vale 1
        val listaEnBase = repo.observarPesajesDePunto(1L).first()
        assertEquals(1, listaEnBase.size)
        assertEquals(-1L, listaEnBase.single().id)
        assertEquals(1L, repo.observarCantidadPendientes().first())
    }

    /**
     * PRUEBA 4: Orden de la sincronizacion (primero sube pendientes POST, despues descarga GET)
     * y sustituye el ID provisional negativo por el ID definitivo positivo sin duplicar filas.
     */
    @Test
    fun `prueba 4 - sincronizacion sube pendientes antes de bajar y reemplaza id negativo por positivo`() = runTest {
        val baseLocal = crearBaseEnMemoria()
        val almacen = AlmacenEnMemoria()
        val ordenPeticiones = mutableListOf<String>()

        // 1. Primero registramos un pesaje sin red (queda con id = -1 y pendiente = true)
        val motorOffline = MockEngine { throw IllegalStateException("Sin red") }
        val repoOffline = EcoRepositoryOfflineImpl(
            baseLocal,
            EcoApiService(crearHttpClient(motorOffline, almacen)),
            almacen
        )
        repoOffline.registrarPesaje(
            puntoId = 1L,
            material = "PET Cristal",
            pesoBruto = 100.0,
            tara = 10.0,
            humedadEstimada = 3.0,
            observacion = "Offline lote 1"
        )
        assertEquals(1, baseLocal.listarPesajesPendientes().size)
        assertEquals(-1L, baseLocal.listarPesajesPendientes().single().id)

        // 2. Recupera la senal: el MockEngine registra el orden exacto de cada llamada HTTP
        val motorOnline = MockEngine { request ->
            val ruta = "${request.method.value} ${request.url.encodedPath}"
            ordenPeticiones.add(ruta)
            when {
                request.method.value == "POST" && request.url.encodedPath.endsWith("/pesajes") -> {
                    respond(
                        content = """
                            {
                              "id": 101,
                              "puntoId": 1,
                              "fechaHora": "2026-10-28T11:00:00",
                              "material": "PET Cristal",
                              "pesoBruto": 100.0,
                              "tara": 10.0,
                              "pesoNeto": 90.0,
                              "humedadEstimada": 3.0,
                              "observacion": "Offline lote 1",
                              "operario": "operario1",
                              "estado": "REGISTRADO"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                request.method.value == "GET" && request.url.encodedPath.endsWith("/rutas/hoy") -> {
                    respond(
                        content = """
                            {
                              "id": 1,
                              "codigo": "RUTA-HOY-01",
                              "fecha": "2026-10-28",
                              "zona": "Multizona",
                              "estado": "ABIERTA",
                              "operario": "operario1",
                              "puntos": [
                                {
                                  "id": 1,
                                  "razonSocial": "Industrias Plasticas del Norte S.A.C.",
                                  "direccion": "Av. Industrial 450",
                                  "zona": "Norte",
                                  "materialHabitual": "PET Cristal",
                                  "secuencia": 1,
                                  "atendido": true
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                else -> {
                    respond(
                        content = """
                            {
                              "contenido": [
                                {
                                  "id": 101,
                                  "puntoId": 1,
                                  "fechaHora": "2026-10-28T11:00:00",
                                  "material": "PET Cristal",
                                  "pesoBruto": 100.0,
                                  "tara": 10.0,
                                  "pesoNeto": 90.0,
                                  "humedadEstimada": 3.0,
                                  "observacion": "Offline lote 1",
                                  "operario": "operario1",
                                  "estado": "REGISTRADO"
                                }
                              ],
                              "pagina": 0,
                              "tamanio": 50,
                              "totalElementos": 1,
                              "totalPaginas": 1,
                              "ultima": true
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        }

        val repoOnline = EcoRepositoryOfflineImpl(
            baseLocal,
            EcoApiService(crearHttpClient(motorOnline, almacen)),
            almacen
        )
        val resumen = repoOnline.sincronizar().getOrThrow()

        // Verifica el orden estricto: 1º POST /pesajes (subir pendiente), 2º GET /rutas/hoy, 3º GET /puntos/1/pesajes
        assertEquals(
            listOf(
                "POST /api/v1/pesajes",
                "GET /api/v1/rutas/hoy",
                "GET /api/v1/puntos/1/pesajes"
            ),
            ordenPeticiones
        )
        assertEquals(1, resumen.enviados)
        assertTrue(baseLocal.listarPesajesPendientes().isEmpty(), "No deben quedar pendientes tras sincronizar")
        assertEquals(0L, repoOnline.observarCantidadPendientes().first())

        val pesajesFinales = repoOnline.observarPesajesDePunto(1L).first()
        assertEquals(1, pesajesFinales.size, "No debe duplicarse la fila tras sincronizar")
        assertEquals(101L, pesajesFinales.single().id, "El ID debe ser el positivo definitivo del servidor")
        assertFalse(pesajesFinales.single().pendiente)
    }

    /**
     * PRUEBA 5 (SC-A): El filtro «Solo pendientes de pesar» combinado con el buscador
     * se resuelve directamente en la consulta SQL local de SQLDelight.
     */
    @Test
    fun `prueba 5 - filtro SC-A solo pendientes de pesar combinado con buscador funciona en SQL local`() = runTest {
        val baseLocal = crearBaseEnMemoria()
        baseLocal.reemplazarPuntosRuta(
            puntosServidor = listOf(
                PuntoAcopio(1L, "Plasticos Norte", "Av. Industrial", "Norte", "PET", 1, atendido = true),
                PuntoAcopio(2L, "Cartonera Sur", "Av. El Sol", "Sur", "Carton", 2, atendido = false),
                PuntoAcopio(3L, "Plasticos Sur", "Panamericana Sur", "Sur", "HDPE", 3, atendido = false)
            ),
            estadoRuta = "ABIERTA",
            fechaHoraSync = "Hoy"
        )

        val soloPendientes = baseLocal.observarPuntosFiltrados(busqueda = "", soloPendientes = true).first()
        assertEquals(2, soloPendientes.size)
        assertTrue(soloPendientes.none { it.atendido })

        val combinado = baseLocal.observarPuntosFiltrados(busqueda = "Plasticos", soloPendientes = true).first()
        assertEquals(1, combinado.size)
        assertEquals("Plasticos Sur", combinado.single().razonSocial)
    }
}
