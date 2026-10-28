package pe.upeu.ecorecicla.config

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import pe.upeu.ecorecicla.dominio.RecursoNoEncontradoException
import pe.upeu.ecorecicla.dominio.ReglaNegocioException
import pe.upeu.ecorecicla.dominio.SinPrivilegioException
import pe.upeu.ecorecicla.dominio.ValidacionCampoException
import pe.upeu.ecorecicla.web.dto.ErrorResponseDto
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * ============================================================================
 * MANEJADOR CENTRALIZADO DE ERRORES DEL SERVICIO (SECCIONES 3.5 Y 4.2)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Preguntas 5 y 11):
 * - Traduce todas las excepciones de validacion, seguridad y reglas de negocio
 *   al formato comun `ErrorResponseDto` de la seccion 4.2:
 *   `timestamp`, `status`, `error`, `message`, `path` y `validationErrors`.
 * - Codigos que emite:
 *   - 400 Bad Request: errores de `@Valid` o de RN-03 (`ValidacionCampoException`), poblando `validationErrors`.
 *   - 401 Unauthorized: credenciales incorrectas o token invalido (`AuthenticationException`).
 *   - 403 Forbidden: falta de privilegio o incumplimiento de RN-02 (`SinPrivilegioException` / `AccessDeniedException`).
 *   - 404 Not Found: punto de acopio, ruta o pesaje inexistente (`RecursoNoEncontradoException`).
 *   - 409 Conflict: incumplimiento de reglas de negocio RN-01 o RN-04 (`ReglaNegocioException`).
 *   - 500 Internal Server Error: fallo inesperado del servidor.
 */
@RestControllerAdvice
class ManejadorDeErrores {

    private val log = LoggerFactory.getLogger(ManejadorDeErrores::class.java)

    private fun ahoraIso(): String =
        LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString()

    /** 400 Bad Request por anotaciones @Valid de Jakarta Validation en los DTOs */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun manejarValidacion(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDto> {
        val erroresPorCampo = linkedMapOf<String, String>()
        ex.bindingResult.fieldErrors.forEach { fieldError ->
            erroresPorCampo[fieldError.field] = fieldError.defaultMessage ?: "Valor inválido"
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponseDto(
                timestamp = ahoraIso(),
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Bad Request",
                message = "Datos invalidos",
                path = request.requestURI,
                validationErrors = erroresPorCampo
            )
        )
    }

    /** 400 Bad Request por regla de negocio de validacion en un campo (ej. RN-03 peso neto <= 0) */
    @ExceptionHandler(ValidacionCampoException::class)
    fun manejarValidacionCampo(
        ex: ValidacionCampoException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponseDto(
                timestamp = ahoraIso(),
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Bad Request",
                message = ex.message ?: "Datos invalidos",
                path = request.requestURI,
                validationErrors = mapOf(ex.campo to (ex.message ?: "Valor inválido"))
            )
        )
    }

    /** 400 Bad Request por parametro de URL mal formado */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun manejarTipoParametro(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponseDto(
                timestamp = ahoraIso(),
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Bad Request",
                message = "El parámetro '${ex.name}' tiene un formato inválido",
                path = request.requestURI,
                validationErrors = mapOf(ex.name to "Valor inválido: '${ex.value}'")
            )
        )
    }

    /** 400 Bad Request por cuerpo JSON ilegible */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun manejarJsonIlegible(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponseDto(
                timestamp = ahoraIso(),
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Bad Request",
                message = "El cuerpo JSON de la petición no es válido o está incompleto",
                path = request.requestURI,
                validationErrors = null
            )
        )
    }

    /** 401 Unauthorized por credenciales incorrectas en el inicio de sesion */
    @ExceptionHandler(AuthenticationException::class)
    fun manejarAutenticacion(
        ex: AuthenticationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponseDto(
                timestamp = ahoraIso(),
                status = HttpStatus.UNAUTHORIZED.value(),
                error = "Unauthorized",
                message = "Usuario o contraseña incorrectos",
                path = request.requestURI,
                validationErrors = null
            )
        )
    }

    /** 403 Forbidden por falta de privilegio o incumplimiento de RN-02 */
    @ExceptionHandler(SinPrivilegioException::class, AccessDeniedException::class)
    fun manejarAccesoDenegado(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponseDto(
                timestamp = ahoraIso(),
                status = HttpStatus.FORBIDDEN.value(),
                error = "Forbidden",
                message = ex.message ?: "No tiene el privilegio requerido para realizar esta operación",
                path = request.requestURI,
                validationErrors = null
            )
        )
    }

    /** 404 Not Found cuando un punto de acopio, ruta o pesaje no existe */
    @ExceptionHandler(RecursoNoEncontradoException::class)
    fun manejarNoEncontrado(
        ex: RecursoNoEncontradoException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponseDto(
                timestamp = ahoraIso(),
                status = HttpStatus.NOT_FOUND.value(),
                error = "Not Found",
                message = ex.message ?: "El recurso solicitado no existe",
                path = request.requestURI,
                validationErrors = null
            )
        )
    }

    /** 409 Conflict cuando se incumple una regla de negocio (RN-01 o RN-04) */
    @ExceptionHandler(ReglaNegocioException::class)
    fun manejarReglaNegocio(
        ex: ReglaNegocioException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponseDto(
                timestamp = ahoraIso(),
                status = HttpStatus.CONFLICT.value(),
                error = "Conflict",
                message = ex.message ?: "Regla de negocio incumplida",
                path = request.requestURI,
                validationErrors = null
            )
        )
    }

    /** 409 Conflict por restriccion de integridad en la base de datos */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun manejarIntegridad(
        ex: DataIntegrityViolationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDto> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponseDto(
                timestamp = ahoraIso(),
                status = HttpStatus.CONFLICT.value(),
                error = "Conflict",
                message = "La operación viola una restricción de integridad de datos",
                path = request.requestURI,
                validationErrors = null
            )
        )
    }

    /** 500 Internal Server Error para cualquier otro fallo no controlado */
    @ExceptionHandler(Exception::class)
    fun manejarInesperado(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDto> {
        if (ex is ErrorResponse) {
            val status = ex.statusCode
            val resolved = HttpStatus.resolve(status.value())
            return ResponseEntity.status(status).body(
                ErrorResponseDto(
                    timestamp = ahoraIso(),
                    status = status.value(),
                    error = resolved?.reasonPhrase ?: "Error",
                    message = ex.body.detail ?: "Error en la petición",
                    path = request.requestURI,
                    validationErrors = null
                )
            )
        }
        log.error("Error inesperado en {}", request.requestURI, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponseDto(
                timestamp = ahoraIso(),
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                error = "Internal Server Error",
                message = "Error interno del servidor. Intente nuevamente.",
                path = request.requestURI,
                validationErrors = null
            )
        )
    }
}
