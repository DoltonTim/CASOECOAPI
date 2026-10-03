package pe.upeu.ecorecicla.seguridad

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pe.upeu.ecorecicla.config.JwtService
import pe.upeu.ecorecicla.dominio.RecursoNoEncontradoException
import pe.upeu.ecorecicla.web.dto.LoginRequestDto
import pe.upeu.ecorecicla.web.dto.LoginResponseDto
import pe.upeu.ecorecicla.web.dto.UsuarioActualDto

/**
 * ============================================================================
 * CONTROLADOR DE AUTENTICACION (RF-B01 Y RF-B07)
 * ============================================================================
 * - POST /api/v1/auth/login -> Valida usuario y contrasena contra la base con
 *   BCrypt y devuelve un JWT firmado con vigencia de 1 hora, rol y privilegios.
 * - GET /api/v1/auth/me     -> Devuelve los datos y privilegios del usuario del token.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val usuarioRepository: UsuarioRepository,
    private val jwtService: JwtService
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequestDto): ResponseEntity<LoginResponseDto> {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.username.trim(), request.password)
        )
        val usuario = usuarioRepository.findByUsernameIgnoreCase(request.username.trim())
            .orElseThrow { RecursoNoEncontradoException("Usuario no encontrado") }

        val token = jwtService.generarToken(usuario)
        val privilegios = PrivilegiosPorRol.nombresDePrivilegios(usuario.rol.nombre)

        return ResponseEntity.ok(
            LoginResponseDto(
                token = token,
                expiraEnSegundos = jwtService.getExpiracionSegundos(),
                usuario = usuario.username,
                nombreCompleto = usuario.nombreCompleto,
                rol = usuario.rol.nombre,
                privilegios = privilegios
            )
        )
    }

    @GetMapping("/me")
    fun usuarioActual(authentication: Authentication): ResponseEntity<UsuarioActualDto> {
        val usuario = usuarioRepository.findByUsernameIgnoreCase(authentication.name)
            .orElseThrow { RecursoNoEncontradoException("Usuario no encontrado: ${authentication.name}") }

        return ResponseEntity.ok(
            UsuarioActualDto(
                id = usuario.id ?: 0L,
                usuario = usuario.username,
                nombreCompleto = usuario.nombreCompleto,
                rol = usuario.rol.nombre,
                privilegios = PrivilegiosPorRol.nombresDePrivilegios(usuario.rol.nombre)
            )
        )
    }
}
