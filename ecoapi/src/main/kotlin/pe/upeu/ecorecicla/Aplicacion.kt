package pe.upeu.ecorecicla

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.security.crypto.password.PasswordEncoder
import pe.upeu.ecorecicla.seguridad.UsuarioRepository

/**
 * ============================================================================
 * PUNTO DE ENTRADA DEL SERVICIO ECOAPI (SECCION 4.7)
 * ============================================================================
 * Se levanta con un solo comando: `.\mvnw.cmd spring-boot:run`
 * Al iniciar:
 * 1. Flyway aplica `V1__esquema_inicial.sql` y `V2__datos_semilla.sql` sobre Oracle Docker.
 * 2. El `CommandLineRunner` verifica que las contrasenas de los usuarios semilla
 *    (`operario1`, `supervisor1`, `admin`, `operario2`, `auditor1`) coincidan con el
 *    hash BCrypt de `Eco2026*` exigido en la Seccion 10 (Anexo).
 */
@SpringBootApplication
class Aplicacion {

    private val log = LoggerFactory.getLogger(Aplicacion::class.java)

    @Bean
    fun asegurarClaveSemilla(
        usuarioRepository: UsuarioRepository,
        passwordEncoder: PasswordEncoder
    ) = CommandLineRunner {
        val claveExamen = "Eco2026*"
        val usuarios = usuarioRepository.findAll()
        usuarios.forEach { usuario ->
            if (!passwordEncoder.matches(claveExamen, usuario.password)) {
                usuario.password = passwordEncoder.encode(claveExamen)!!
                usuarioRepository.save(usuario)
                log.info("Contrasena BCrypt sincronizada para usuario de prueba: {}", usuario.username)
            }
        }
        log.info("EcoAPI lista en http://localhost:8080/api/v1 (Usuarios activos: {})", usuarios.size)
    }
}

fun main(args: Array<String>) {
    runApplication<Aplicacion>(*args)
}
