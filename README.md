# EcoRutas — Aplicación KMP Android Offline-First (Versión B · Unidad 2)

**Estudiante:** Dolton Meza  
**Asignatura:** Desarrollo de Aplicaciones Móviles — Ciclo VI · 2026-2  
**Plataforma:** Android · Kotlin Multiplatform + Compose Multiplatform + Ktor + SQLDelight + Koin + EncryptedSharedPreferences

---

## 1. Instrucciones de ejecución

1. Asegúrate de que el backend **EcoAPI** esté levantado en `http://localhost:8080` (`.\mvnw.cmd spring-boot:run`).
2. El cliente Ktor de **EcoRutas** apunta centralizadamente a `http://10.0.2.2:8080/api/v1/` (`ApiConfig.kt`), que desde el emulador de Android resuelve al `localhost` de la estación de trabajo.
3. Para ejecutar las pruebas de `commonTest` (con `MockEngine` de Ktor y `JdbcSqliteDriver.IN_MEMORY` de SQLDelight):
   ```powershell
   .\gradlew.bat :shared:testAndroidHostTest
   ```
4. Para compilar el APK de Android:
   ```powershell
   .\gradlew.bat :androidApp:assembleDebug
   ```

---

## 2. Estructura de paquetes (Sección 4.7)

```text
shared/src/commonMain/kotlin/pe/upeu/ecorecicla/
├── domain/
│   ├── model/       PuntoAcopio.kt, EstadoPesaje.kt, Pesaje.kt, Sesion.kt, ErrorApi.kt
│   ├── repository/  AuthRepository.kt, EcoRepository.kt (solo contratos)
│   └── usecase/     SincronizarUseCase.kt, ObservarRutaUseCase.kt, RegistrarPesajeUseCase.kt, etc.
├── data/
│   ├── remote/      ApiConfig.kt, ClienteHttp.kt, MapeoErrores.kt, EcoApiService.kt
│   │   └── dto/     EcoDtos.kt (PaginaResponseDto, ErrorResponseDto y funciones .aDominio())
│   ├── local/       AlmacenSeguro.kt, BaseLocal.kt (SQLDelight con flujos observables e IDs negativos)
│   └── repository/  AuthRepositoryImpl.kt, EcoRepositoryOfflineImpl.kt
├── presentation/    sesion/, listado/, detalle/, perfil/, navigation/ y theme/
└── di/              AppModules.kt (redModule, dataModule, domainModule, presentationModule, platformModule)

androidApp/src/main/kotlin/pe/upeu/ecorecicla/
├── AlmacenSeguroAndroid.kt   # EncryptedSharedPreferences + MasterKey (AES256_GCM en AndroidKeyStore)
├── BaseLocalAndroid.kt       # AndroidSqliteDriver para SQLDelight
└── PlatformModule.android.kt # Motor HTTP OkHttp y dependencias nativas de Android
```

---

## 3. Decisiones de arquitectura y estrategia Offline-First

1. **Lectura exclusiva desde la base local (`BaseLocal.kt`):** La pantalla principal (`ListadoRutaScreen`) observa `queries.filtrarPuntosRuta(...).asFlow().mapToList()`, nunca la respuesta directa de la red. Así el operario puede trabajar en sótanos sin señal.
2. **Identificadores locales provisionales negativos (`< 0`):** Cuando se registra un pesaje sin conexión, `siguienteIdLocal` (`SELECT COALESCE(MIN(id), 0) FROM pesaje`) calcula `minOf(menor, 0L) - 1L` (`-1`, `-2`, ...) y marca `pendiente = 1`. Nunca colisiona con los identificadores positivos emitidos por el servidor.
3. **Orden de la sincronización (`EcoRepositoryOfflineImpl.sincronizar`):**
   - **Primero** envía los registros con `pendiente = 1` (`POST /api/v1/pesajes`) y sustituye la fila provisional negativa por la definitiva positiva dentro de la misma transacción.
   - **Después** descarga la ruta del día (`GET /api/v1/rutas/hoy`) y los pesajes de cada punto (`GET /api/v1/puntos/{id}/pesajes`).
4. **Seguridad y turno compartido:** El token JWT se almacena cifrado en `EncryptedSharedPreferences` (`AlmacenSeguroAndroid`). La cabecera `Authorization: Bearer <token>` se añade en un único punto (`ClienteHttp.kt`). Un `401` borra el token y regresa al login; un `403` informa la falta de privilegio y mantiene la sesión.
