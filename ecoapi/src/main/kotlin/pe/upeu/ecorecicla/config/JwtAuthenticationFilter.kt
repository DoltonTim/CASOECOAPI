package pe.upeu.ecorecicla.config

import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter

/**
 * ============================================================================
 * FILTRO DE SEGURIDAD JWT POR PETICION (SECCION 4.1)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Preguntas 1 y 2):
 * - Intercepta cada peticion HTTP antes de llegar al controlador.
 * - Extrae la cabecera `Authorization: Bearer <token>`.
 * - Verifica la firma criptografica y la fecha de expiracion mediante [JwtService].
 * - Si el token fue alterado, venció o está ausente, no establece autenticacion en el
 *   `SecurityContextHolder` y la cadena de seguridad devuelve `401 Unauthorized`.
 */
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userDetailsService: UserDetailsService
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)

        if (header.isNullOrBlank() || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = header.substring(7).trim()

        try {
            val username = jwtService.extraerUsername(token)

            if (SecurityContextHolder.getContext().authentication == null) {
                val userDetails = userDetailsService.loadUserByUsername(username)
                if (userDetails.isEnabled) {
                    val authentication = UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.authorities
                    )
                    authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = authentication
                }
            }
        } catch (ex: JwtException) {
            log.debug("Token JWT rechazado (alterado o vencido) en {}: {}", request.requestURI, ex.message)
            SecurityContextHolder.clearContext()
        } catch (ex: UsernameNotFoundException) {
            log.debug("Usuario del token no existe en {}: {}", request.requestURI, ex.message)
            SecurityContextHolder.clearContext()
        } catch (ex: IllegalArgumentException) {
            log.debug("Token JWT vacio o mal formado en {}: {}", request.requestURI, ex.message)
            SecurityContextHolder.clearContext()
        }

        filterChain.doFilter(request, response)
    }
}
