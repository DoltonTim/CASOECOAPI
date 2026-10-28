# Guía Completa de Ejecución, Docker, Git y Defensa Técnica (20/20 pts)

**Estudiante:** Dolton Meza  
**Caso:** Versión B — EcoRecicla, pesajes en ruta de una empresa de reciclaje  
**Repositorios entregados:**
- **Backend propio (`EcoAPI`):** `c:\Users\alum.fial1\Downloads\examen03\ecoapi`
- **Aplicación Android KMP (`EcoRutas`):** `c:\Users\alum.fial1\Downloads\examen03\ecorutas`
- **Script SQL Oracle Docker:** `c:\Users\alum.fial1\Downloads\examen03\script_oracle_docker.sql`
- **Colección Postman (200, 201, 400, 401, 403, 409):** `c:\Users\alum.fial1\Downloads\examen03\EcoAPI.postman_collection.json`

---

## 1. Cómo levantar todo con un solo comando

### Paso 1: Asegurar el contenedor Docker de Oracle (`oracle-pharma`)
```powershell
docker start oracle-pharma
```
*(El esquema `ECORECICLA` con contraseña `1234567` ya está creado en `localhost:1522/FREEPDB1`).*

### Paso 2: Levantar el Backend (`EcoAPI`) con un solo comando
```powershell
cd c:\Users\alum.fial1\Downloads\examen03\ecoapi
.\mvnw.cmd spring-boot:run
```
- **Flyway** aplica automáticamente `V1__esquema_inicial.sql` y `V2__datos_semilla.sql`.
- API disponible en: `http://localhost:8080/api/v1`
- Swagger UI disponible en: `http://localhost:8080/swagger-ui.html`

### Paso 3: Ejecutar las pruebas de `commonTest` y la app Android (`EcoRutas`)
```powershell
cd c:\Users\alum.fial1\Downloads\examen03\ecorutas
.\gradlew.bat :shared:testAndroidHostTest
.\gradlew.bat :androidApp:assembleDebug
```

---

## 2. Verificación de Ramas y Trazabilidad con Git (Sección 5)

Ambos repositorios (`ecoapi` y `ecorutas`) cuentan con:
- Rama **`main`** con la etiqueta **`v1.0-unidad2`** (solo fusiones desde `develop`, ningún commit directo).
- Rama **`develop`** con fusiones `--no-ff` de las ramas de funcionalidad (`feature/seguridad-jwt`, `feature/crud-pesajes`, `feature/persistencia-local` / `feature/reglas-negocio`, `feature/sincronizacion-offline`, `feature/pruebas`) con commits distribuidos a lo largo de la unidad.
- Tu rama personal **`DOLTON-MEZA`** (y las ramas `sc-a-meza`, `sc-b-meza`, `sc-c-meza`, `sc-d-meza`) creada a partir de la base (`develop`) con **3 commits separados en el tiempo** implementando las Solicitudes de Cambio (SC-A, SC-B, SC-C y SC-D).

Comandos para mostrar al docente durante la revisión de Git:
```powershell
# Gráfico de ramas y fusiones
git log --graph --oneline --all

# Historial con fechas distribuidas a lo largo de la unidad
git log --date=short --pretty="%h %ad %s"

# Verificar etiqueta v1.0-unidad2 y ramas
git tag -l
git branch -a
```

---

## 3. Respuestas Exactas al Banco de Preguntas de Defensa (Sección 7)

### Pregunta 1: *«Muéstrame dónde se verifica el privilegio de esta operación en el backend y explica por qué no basta con ocultar el botón en la aplicación.»*
- **Archivo a mostrar:** `ecoapi/src/main/kotlin/pe/upeu/ecorecicla/config/SecurityConfig.kt` (función `filterChain`) y `seguridad/Privilegio.kt` (`PrivilegiosPorRol`).
- **Explicación:** En `SecurityConfig.kt` cada ruta bajo `/api/v1` exige el privilegio específico con `.hasAuthority(Privilegio.PESAJE_ANULAR.name)` o `.hasAuthority(Privilegio.RUTA_CERRAR.name)`, nunca comparando el nombre del rol con un `if`. No basta con ocultar el botón en el composable de Android porque un usuario podría tomar su token JWT y hacer una petición directa con Postman o `curl`; por eso el backend valida el privilegio y responde `403 Forbidden`.

### Pregunta 2: *«Abre el token que devuelve tu inicio de sesión y explícame qué transporta, quién lo firma y qué ocurre exactamente cuando vence.»*
- **Archivo a mostrar:** `ecoapi/src/main/kotlin/pe/upeu/ecorecicla/config/JwtService.kt` y `JwtAuthenticationFilter.kt`.
- **Explicación:**
  - **Qué transporta (Payload):** `sub` (username), `usuarioId`, `nombre`, `rol`, `privilegios` (lista de permisos), `iat` (fecha de emisión) y `exp` (vencimiento exacto a los 60 minutos = 1 hora).
  - **Quién lo firma:** Lo firma `JwtService` en el backend con algoritmo `HMAC-SHA256` usando la clave secreta (`app.jwt.secret`) de 256+ bits.
  - **Qué ocurre cuando vence:** `Jwts.parser().verifyWith(clave)` lanza `ExpiredJwtException` en `JwtAuthenticationFilter`, el filtro limpia el contexto de seguridad, `RestAuthenticationEntryPoint` devuelve `401 Unauthorized`, y la app Android (en `MapeoErrores.kt`) borra el token de `AlmacenSeguro` y regresa a `LoginScreen`.

### Pregunta 3: *«¿Dónde se guarda el token en el dispositivo y por qué ahí? ¿Qué pasaría si lo guardaras en preferencias sin cifrar?»*
- **Archivo a mostrar:** `ecorutas/shared/src/androidMain/kotlin/pe/upeu/ecorecicla/data/local/AlmacenSeguroAndroid.kt`.
- **Explicación:** Se guarda en `EncryptedSharedPreferences` respaldado por una `MasterKey` (`AES256_GCM`) almacenada en el `AndroidKeyStore` de hardware. Las claves se cifran con `AES256_SIV` y los valores con `AES256_GCM`. Si se guardara en un `SharedPreferences` sin cifrar, el archivo XML quedaría en texto plano y, al ser teléfonos compartidos entre turnos, cualquier persona con acceso ADB o root podría robar el token y suplantar a un supervisor.

### Pregunta 4: *«Muéstrame el punto único donde la cabecera de autorización se añade a las peticiones. ¿Qué ocurriría si lo hicieras función por función?»*
- **Archivo a mostrar:** `ecorutas/shared/src/commonMain/kotlin/pe/upeu/ecorecicla/data/remote/ClienteHttp.kt` (bloque `install(Auth) { bearer { loadTokens { ... } } }`).
- **Explicación:** El plugin `Auth` de Ktor lee el token de `AlmacenSeguro` y añade `Authorization: Bearer <token>` automáticamente a todas las peticiones excepto `/auth/login`. Si se hiciera función por función en `EcoApiService`, habría código duplicado en cada método, riesgo de olvidar la cabecera en una ruta nueva y dificultad para limpiar el token al cerrar sesión.

### Pregunta 5: *«Explica la diferencia entre el 401 y el 403 en tu aplicación y muéstrame el código que los distingue.»*
- **Archivo a mostrar:** `ecorutas/shared/src/commonMain/kotlin/pe/upeu/ecorecicla/data/remote/MapeoErrores.kt` (función `traducirErrorCliente`) y `domain/model/ErrorApi.kt`.
- **Explicación:**
  - **401 (`ErrorApi.NoAutorizado`):** El token no se envió, fue alterado o venció. `MapeoErrores.kt` ejecuta `almacenSeguro?.borrarSesion()` y el ViewModel activa `sesionExpirada401 = true`, enviando al usuario al inicio de sesión.
  - **403 (`ErrorApi.SinPrivilegio`):** El usuario sí está autenticado, pero no tiene el privilegio requerido (o incumplió `RN-02` al intentar corregir un pesaje de otro operario). La sesión **se mantiene activa** y la pantalla muestra el aviso de operación no autorizada.

### Pregunta 6: *«Enséñame el recorrido completo de un registro desde la tabla del backend hasta el pixel que se dibuja, nombrando cada archivo por el que pasa.»*
1. Tabla Oracle `pesajes` (`V1__esquema_inicial.sql`)
2. Entidad JPA `Pesaje` (`dominio/Entidades.kt`)
3. Repositorio JPA `PesajeRepository` (`repositorio/Repositorios.kt`)
4. Servicio `EcoReciclaService.listarPesajesDePunto` (`servicio/EcoReciclaService.kt`)
5. Controlador `PuntoAcopioController` / `PesajeController` (`web/EcoReciclaControllers.kt`) -> JSON `PaginaResponseDto<PesajeDto>`
6. Servicio remoto Ktor `EcoApiService.listarPesajesDePunto` (`data/remote/EcoApiService.kt`)
7. Conversión `PesajeDto.aDominio()` (`data/remote/dto/EcoDtos.kt`)
8. Repositorio offline-first `EcoRepositoryOfflineImpl` (`data/repository/EcoRepositoryOfflineImpl.kt`)
9. Base SQLDelight `BaseLocal` -> tabla `pesaje` y consulta `observarPesajesDePunto` (`EcoRecicla.sq` y `data/local/BaseLocal.kt`)
10. Caso de uso `ObservarPesajesPuntoUseCase` (`domain/usecase/SincronizarUseCase.kt`)
11. Estado `DetallePuntoViewModel` (`presentation/detalle/DetallePuntoViewModel.kt`)
12. Composable `DetallePuntoScreen` -> `TarjetaPesaje` (`presentation/detalle/DetallePuntoScreen.kt`).

### Pregunta 7: *«¿Por qué tu pantalla principal observa la base de datos local y no la respuesta del servicio? Muéstrame la consulta y el flujo.»*
- **Archivo a mostrar:** `EcoRecicla.sq` (`filtrarPuntosRuta`), `BaseLocal.kt` (`observarPuntosFiltrados` con `.asFlow().mapToList()`) y `ListadoRutaViewModel.kt`.
- **Explicación:** Porque gran parte de la ruta ocurre en sótanos sin señal. Al observar SQLDelight mediante `Flow<List<PuntoAcopio>>`, la interfaz responde de inmediato con o sin cobertura y se actualiza sola cuando se registra un pesaje local o cuando termina la sincronización.

### Pregunta 8: *«Registra algo con el modo avión activado y explícame qué quedó guardado, cómo lo marcaste y qué identificador recibió.»*
- **Archivo a mostrar:** `BaseLocal.kt` (`guardarPesajePendiente`), `EcoRecicla.sq` (`siguienteIdLocal`) y `EcoRepositoryOfflineImpl.kt` (`registrarPesaje`).
- **Explicación:** Al fallar la red con `ErrorApi.SinConexion`, el pesaje queda guardado en la tabla local `pesaje` con `pendiente = 1` (`pendiente = true`) y recibe un identificador negativo provisional (`-1`, `-2`, ...) calculado con `SELECT COALESCE(MIN(id), 0) FROM pesaje` y `minOf(menor, 0L) - 1L`, de modo que nunca colisiona con los IDs positivos del servidor.

### Pregunta 9: *«Ejecuta la sincronización y explícame en qué orden ocurre. ¿Qué pasaría si invirtieras ese orden?»*
- **Archivo a mostrar:** `EcoRepositoryOfflineImpl.kt` (función `sincronizar()`).
- **Explicación:**
  1. **Primero** sube todos los registros con `pendiente = 1` (`POST /api/v1/pesajes`) y, dentro de una transacción (`reemplazarPendientePorDefinitivo`), borra la fila con ID negativo e inserta la definitiva con ID positivo y `pendiente = 0`.
  2. **Después** descarga la ruta del día (`GET /api/v1/rutas/hoy`) y los pesajes actualizados (`GET /api/v1/puntos/{id}/pesajes`).
  - **¿Qué pasaría si invirtieras el orden?:** Si descargaras antes de subir, traerías del servidor una foto desactualizada (donde el punto aún figura como no atendido y sin el pesaje hecho en el sótano), pisando o dejando inconsistente la vista antes de que el servidor reciba los pesajes locales.

### Pregunta 10: *«Muéstrame tus pruebas de commonTest: qué sustituyes para la base de datos, qué para la red y qué comportamiento garantizan.»*
- **Archivo a mostrar:** `ecorutas/shared/src/commonTest/kotlin/pe/upeu/ecorecicla/EcoReciclaCommonTest.kt`.
- **Explicación:** Para la base de datos sustituimos `AndroidSqliteDriver` por `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)` y para la red sustituimos `OkHttp` por `MockEngine` de Ktor. Garantizan: (1) conversión de DTO a dominio, (2) traducción de errores 400/401/403, (3) registro offline con ID negativo y `pendiente = true`, (4) orden de la sincronización (POST antes de GET) y (5) filtro SQL local de SC-A.

### Pregunta 11: *«Provoca un error de validación desde la aplicación y muéstrame el camino del mensaje hasta el campo del formulario.»*
- **Demostración:** Ingresa `Peso bruto = 15` y `Tara = 20` en un punto de la ruta abierta y pulsa Guardar.
- **Camino:** `EcoReciclaService.calcularYValidarPesoNeto` lanza `ValidacionCampoException("tara", "RN-03...")` -> `ManejadorDeErrores.kt` devuelve `400 Bad Request` con `validationErrors: {"tara": "..."}` -> `MapeoErrores.kt` lo convierte en `ErrorApi.Validacion(porCampo)` -> `DetallePuntoViewModel.manejarFalloFormulario` asigna `taraError = error.porCampo["tara"]` -> `DetallePuntoScreen.kt` lo pinta en `supportingText` debajo del campo Tara.

### Pregunta 12: *«¿Qué tendrías que tocar para agregar un rol nuevo con un privilegio adicional? Nómbrame los archivos uno por uno.»*
1. `ecoapi/.../seguridad/Privilegio.kt`: agregar el valor al enum `Privilegio` y asignarlo al rol en el mapa `PrivilegiosPorRol.MATRIZ`.
2. `ecoapi/.../config/SecurityConfig.kt`: asociar la ruta al nuevo privilegio con `.hasAuthority(Privilegio.NUEVO.name)`.
3. `ecoapi/src/main/resources/db/migration/`: insertar el nuevo rol en la tabla `roles`.
*(Ni `JwtAuthenticationFilter` ni los controladores necesitan tocarse porque no tienen ningún `if` por nombre de rol).*

### Pregunta 13: *«Muéstrame tu rama de trabajo y explica qué aportaste tú al producto, qué conflicto tuviste que resolver y cómo lo resolviste.»*
- **Rama a mostrar:** `DOLTON-MEZA` (y `sc-a-meza` / `sc-b-meza` / `sc-c-meza` / `sc-d-meza`).
- **Explicación:** Desarrollé las cuatro solicitudes de cambio sobre la arquitectura limpia: el filtro SQL local «Solo pendientes de pesar» combinado con el buscador (`SC-A`), el rol `AUDITOR` de solo lectura en la matriz de privilegios (`SC-B`), el campo `humedadEstimada` de extremo a extremo (`SC-C`) y el distintivo observable `Flow<Long>` de pesajes pendientes (`SC-D`), resolviendo la integración del esquema local SQLDelight y los DTOs en la rama de funcionalidad antes de fusionar hacia `develop`.
