package pe.upeu.ecorecicla.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.stereotype.Component
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import pe.upeu.ecorecicla.seguridad.Privilegio
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * ============================================================================
 * CONFIGURACION DE SEGURIDAD Y AUTORIZACION POR PRIVILEGIO (SECCIONES 3.3 Y 4.1)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Preguntas 1, 5 y 12):
 * - ¿Donde se verifica el privilegio?: En este filtro central (`SecurityFilterChain`)
 *   mediante `.hasAuthority(Privilegio.X.name)`. NUNCA se compara el nombre del rol en un `if`.
 * - ¿Por que no basta con ocultar el boton en la app?: Porque cualquier cliente externo
 *   (Postman, curl o un APK modificado) podria invocar directamente el endpoint HTTP con su token.
 *   Por eso el backend valida el privilegio y rechaza con `403 Forbidden` si el usuario no lo posee.
 * - Diferencia entre 401 y 403:
 *   - 401 (`RestAuthenticationEntryPoint`): Token ausente, alterado o vencido (no sabemos quien es).
 *   - 403 (`RestAccessDeniedHandler`): Token valido (sabemos quien es), pero no tiene el privilegio exigido.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    private val rutasPublicas = arrayOf(
        "/api/v1/auth/login",
        "/api/v1/health",
        "/api/health",
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/error"
    )

    @Bean
    fun filterChain(
        http: HttpSecurity,
        jwtService: JwtService,
        userDetailsService: UserDetailsService,
        corsConfigurationSource: CorsConfigurationSource,
        authenticationEntryPoint: RestAuthenticationEntryPoint,
        accessDeniedHandler: RestAccessDeniedHandler
    ): SecurityFilterChain {
        val jwtFilter = JwtAuthenticationFilter(jwtService, userDetailsService)

        http
            .csrf { it.disable() }
            .logout { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // 1. Rutas publicas (inicio de sesion y documentacion OpenAPI)
                    .requestMatchers(*rutasPublicas).permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                    // 2. Usuario actual (RF-B07): cualquier usuario autenticado con token valido
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()

                    // 3. Cierre de ruta del dia (RF-B06): exige privilegio RUTA_CERRAR
                    .requestMatchers(HttpMethod.POST, "/api/v1/rutas/*/cerrar", "/api/v1/rutas/hoy/cerrar")
                    .hasAuthority(Privilegio.RUTA_CERRAR.name)
                    .requestMatchers(HttpMethod.PUT, "/api/v1/rutas/*/cerrar", "/api/v1/rutas/hoy/cerrar")
                    .hasAuthority(Privilegio.RUTA_CERRAR.name)

                    // 4. Consulta de ruta del dia (RF-B02): exige privilegio PUNTO_LEER
                    .requestMatchers(HttpMethod.GET, "/api/v1/rutas/**")
                    .hasAuthority(Privilegio.PUNTO_LEER.name)

                    // 5. Pesajes de un punto (RF-B03 / 4.3): exige privilegio PESAJE_LEER
                    .requestMatchers(HttpMethod.GET, "/api/v1/puntos/*/pesajes", "/api/v1/pesajes/**")
                    .hasAuthority(Privilegio.PESAJE_LEER.name)

                    // 6. Registro de pesaje (RF-B04): exige privilegio PESAJE_CREAR
                    .requestMatchers(HttpMethod.POST, "/api/v1/pesajes", "/api/v1/puntos/*/pesajes")
                    .hasAuthority(Privilegio.PESAJE_CREAR.name)

                    // 7. Anulacion de pesaje (RF-B05): exige privilegio PESAJE_ANULAR
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/pesajes/*/anular")
                    .hasAuthority(Privilegio.PESAJE_ANULAR.name)
                    .requestMatchers(HttpMethod.POST, "/api/v1/pesajes/*/anular")
                    .hasAuthority(Privilegio.PESAJE_ANULAR.name)
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/pesajes/*")
                    .hasAuthority(Privilegio.PESAJE_ANULAR.name)

                    // 8. Correccion de pesaje (RF-B05): exige privilegio PESAJE_EDITAR
                    .requestMatchers(HttpMethod.PUT, "/api/v1/pesajes/*")
                    .hasAuthority(Privilegio.PESAJE_EDITAR.name)

                    // 9. Puntos de acopio (RF-B03): lectura exige PUNTO_LEER, gestion exige PUNTO_GESTIONAR
                    .requestMatchers(HttpMethod.GET, "/api/v1/puntos/**")
                    .hasAuthority(Privilegio.PUNTO_LEER.name)
                    .requestMatchers("/api/v1/puntos/**")
                    .hasAuthority(Privilegio.PUNTO_GESTIONAR.name)

                    // 10. Cualquier otra ruta bajo /api/v1 exige autenticacion
                    .anyRequest().authenticated()
            }
            .exceptionHandling { errores ->
                errores
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(configuration: AuthenticationConfiguration): AuthenticationManager =
        configuration.authenticationManager

    @Bean
    fun corsConfigurationSource(
        @Value("\${app.cors.allowed-origins:http://localhost:5173}") origenes: String
    ): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = origenes.split(",").map { it.trim() }
        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}

/**
 * Manejador para respuestas 401 Unauthorized en el filtro de seguridad,
 * respetando el formato de error comun de la seccion 4.2.
 */
@Component
class RestAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        escribirJsonError(
            response = response,
            status = HttpStatus.UNAUTHORIZED,
            mensaje = "No autenticado: el token está ausente, fue alterado o ha expirado",
            path = request.requestURI
        )
    }
}

/**
 * Manejador para respuestas 403 Forbidden en el filtro de seguridad,
 * cuando el usuario tiene un token valido pero carece del privilegio requerido.
 */
@Component
class RestAccessDeniedHandler : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        escribirJsonError(
            response = response,
            status = HttpStatus.FORBIDDEN,
            mensaje = "Acceso denegado: no posee el privilegio requerido para esta operación",
            path = request.requestURI
        )
    }
}

private fun escribirJsonError(
    response: HttpServletResponse,
    status: HttpStatus,
    mensaje: String,
    path: String
) {
    val timestamp = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString()
    val json = """
        {
          "timestamp": "$timestamp",
          "status": ${status.value()},
          "error": "${status.reasonPhrase}",
          "message": "$mensaje",
          "path": "$path",
          "validationErrors": null
        }
    """.trimIndent()
    response.status = status.value()
    response.characterEncoding = StandardCharsets.UTF_8.name()
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    response.writer.write(json)
}
