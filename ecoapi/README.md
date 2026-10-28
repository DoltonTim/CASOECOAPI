# EcoAPI — Servicio REST de EcoRecicla (Versión B · Unidad 2)

**Estudiante:** Dolton Meza  
**Asignatura:** Desarrollo de Aplicaciones Móviles — Ciclo VI · 2026-2  
**Plataforma:** Backend propio (Kotlin + Spring Boot 4 + JWT + Flyway + Oracle Database 26ai en Docker)

---

## 1. Instrucciones de ejecución (un solo comando)

### 1.1 Base de datos en Docker (`oracle-pharma`)
El servicio se conecta al contenedor Docker `oracle-pharma` (Oracle Database Free 26ai en el puerto `1522`, servicio `FREEPDB1`, esquema `ECORECICLA` / `1234567`):

```powershell
# Si el contenedor ya existe:
docker start oracle-pharma

# O usando Docker Compose incluido en este repositorio:
docker compose up -d
```

### 1.2 Levantar el servicio EcoAPI con un solo comando
```powershell
.\mvnw.cmd spring-boot:run
```
Al arrancar:
- **Flyway** ejecuta automáticamente `V1__esquema_inicial.sql` y `V2__datos_semilla.sql` (crea las tablas y carga los 12 puntos de acopio en 3 zonas, las 2 rutas, los 15 pesajes y los usuarios semilla).
- El servicio queda disponible en: `http://localhost:8080/api/v1` (y para el emulador Android en `http://10.0.2.2:8080/api/v1/`).
- Documentación interactiva **Swagger UI**: `http://localhost:8080/swagger-ui.html`.

---

## 2. Estructura de paquetes (Sección 4.7)

```text
src/main/kotlin/pe/upeu/ecorecicla/
├── config/
│   ├── SecurityConfig.kt           # Cadena de filtros, rutas protegidas por privilegio, 401 y 403
│   ├── JwtAuthenticationFilter.kt  # Verificación de firma HMAC-SHA256 y expiración por petición
│   ├── JwtService.kt               # Emisión y lectura de JWT con vigencia de 1 hora
│   └── ManejadorDeErrores.kt       # @RestControllerAdvice con el formato común de error (4.2)
├── seguridad/
│   ├── Privilegio.kt               # Enum Privilegio y matriz PrivilegiosPorRol (sin if de rol)
│   ├── Rol.kt                      # Entidad JPA roles
│   ├── Usuario.kt                  # Entidad JPA usuarios (clave cifrada con BCrypt)
│   ├── UsuarioRepository.kt        # Repositorios JPA y UsuarioDetailsService
│   └── AuthController.kt           # POST /api/v1/auth/login y GET /api/v1/auth/me
├── dominio/
│   └── Entidades.kt                # PuntoAcopio, RutaDia, RutaPunto, Pesaje y excepciones de negocio
├── repositorio/
│   └── Repositorios.kt             # Acceso JPA/SQL a puntos, rutas y pesajes paginados
├── servicio/
│   └── EcoReciclaService.kt        # Coordinación transaccional y reglas RN-01, RN-02, RN-03 y RN-04
├── web/
│   ├── EcoReciclaControllers.kt    # Controladores REST bajo /api/v1
│   └── dto/
│       └── Dtos.kt                 # PaginaResponseDto<T>, ErrorResponseDto y DTOs de entrada/salida
└── Aplicacion.kt                   # Punto de entrada de Spring Boot
```

---

## 3. Roles y matriz de privilegios (Sección 3.3 + SC-B)

La autorización se verifica siempre contra el **privilegio** (`hasAuthority(Privilegio.X.name)`), nunca contra el nombre del rol en un controlador:

| Privilegio | Permite | OPERARIO | SUPERVISOR | ADMINISTRADOR | AUDITOR (SC-B) |
| :--- | :--- | :---: | :---: | :---: | :---: |
| `PUNTO_LEER` | Consultar la ruta y los puntos de acopio | Sí | Sí | Sí | Sí |
| `PESAJE_LEER` | Consultar los pesajes de un punto | Sí | Sí | Sí | Sí |
| `PESAJE_CREAR` | Registrar un pesaje nuevo | Sí | Sí | No | No |
| `PESAJE_EDITAR` | Corregir un pesaje existente | Solo propios (RN-02) | Todos | No | No |
| `PESAJE_ANULAR` | Anular un pesaje | No | Sí | No | No |
| `RUTA_CERRAR` | Cerrar la ruta del día | No | Sí | No | No |
| `PUNTO_GESTIONAR` | Crear puntos de acopio y asignar rutas | No | No | Sí | No |

### Usuarios semilla (Sección 10)
| Usuario | Contraseña | Rol |
| :--- | :--- | :--- |
| `operario1` | `Eco2026*` | `OPERARIO` |
| `supervisor1` | `Eco2026*` | `SUPERVISOR` |
| `admin` | `Eco2026*` | `ADMINISTRADOR` |
| `operario2` | `Eco2026*` | `OPERARIO` (para demostrar RN-02) |
| `auditor1` | `Eco2026*` | `AUDITOR` (SC-B) |

---

## 4. Contrato del API bajo `/api/v1` (Sección 4.3)

| Operación | Método y Ruta | Privilegio exigido | Éxito | Errores posibles |
| :--- | :--- | :--- | :---: | :---: |
| Inicio de sesión (RF-B01) | `POST /api/v1/auth/login` | Público | `200 OK` | `400`, `401` |
| Usuario actual (RF-B07) | `GET /api/v1/auth/me` | Autenticado | `200 OK` | `401` |
| Ruta del día (RF-B02) | `GET /api/v1/rutas/hoy` | `PUNTO_LEER` | `200 OK` | `401`, `403`, `404` |
| Listado de puntos (RF-B03) | `GET /api/v1/puntos?zona=&pagina=0&tamanio=20` | `PUNTO_LEER` | `200 OK` | `401`, `403` |
| Crear punto de acopio | `POST /api/v1/puntos` | `PUNTO_GESTIONAR` | `201 Created` | `400`, `401`, `403` |
| Pesajes de un punto | `GET /api/v1/puntos/{puntoId}/pesajes?pagina=0&tamanio=20` | `PESAJE_LEER` | `200 OK` | `401`, `403`, `404` |
| Registrar pesaje (RF-B04) | `POST /api/v1/pesajes` | `PESAJE_CREAR` | `201 Created` | `400` (RN-03), `401`, `403`, `409` (RN-01) |
| Corregir pesaje (RF-B05) | `PUT /api/v1/pesajes/{id}` | `PESAJE_EDITAR` | `200 OK` | `400` (RN-03), `401`, `403` (RN-02), `404`, `409` (RN-01) |
| Anular pesaje (RF-B05) | `PATCH /api/v1/pesajes/{id}/anular` | `PESAJE_ANULAR` | `200 OK` | `400`, `401`, `403`, `404`, `409` |
| Cerrar ruta del día (RF-B06) | `POST /api/v1/rutas/hoy/cerrar` | `RUTA_CERRAR` | `200 OK` | `401`, `403`, `409` (RN-04) |

---

## 5. Reglas de negocio en `EcoReciclaService` (Sección 3.4)

- **RN-01 (`409 Conflict`):** No se puede registrar ni corregir ningún pesaje en una ruta ya cerrada (`RUTA-AYER-01`, puntos 7 al 12).
- **RN-02 (`403 Forbidden`):** Un operario solo puede corregir los pesajes que él mismo registró y únicamente dentro del mismo día.
- **RN-03 (`400 Bad Request`):** El `pesoNeto` lo calcula el servicio (`pesoBruto - tara`) y debe ser `> 0`. Si no lo es, responde `400` con el error en `validationErrors["tara"]`.
- **RN-04 (`409 Conflict`):** La ruta no puede cerrarse mientras algún punto planificado no tenga un pesaje registrado o un motivo de omisión. Responde `409` indicando cuántos puntos faltan.
