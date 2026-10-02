package pe.upeu.ecorecicla.config

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import pe.upeu.ecorecicla.seguridad.PrivilegiosPorRol
import pe.upeu.ecorecicla.seguridad.Usuario
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.crypto.SecretKey

/**
 * ============================================================================
 * SERVICIO DE FIRMA Y VERIFICACION DE TOKENS JWT (SECCION 4.1)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Pregunta 2):
 * - Que transporta el token:
 *   1. `sub` (Subject): el identificador (username) del usuario autenticado.
 *   2. `rol`: el nombre del rol del usuario (ej. OPERARIO, SUPERVISOR, ADMINISTRADOR, AUDITOR).
 *   3. `privilegios`: la lista de privilegios efectivos del usuario (ej. PUNTO_LEER, PESAJE_CREAR...).
 *   4. `iat` (Issued At) y `exp` (Expiration): marca de emision y vencimiento exacto de 1 hora (60 min).
 * - Quien lo firma:
 *   Lo firma el backend (EcoAPI) mediante algoritmo HMAC-SHA256 con la clave secreta (`SecretKey`)
 *   configurada en `application.yaml`.
 * - Que ocurre cuando vence o se altera:
 *   `Jwts.parser().verifyWith(clave).build().parseSignedClaims(token)` lanza `ExpiredJwtException`
 *   o `SignatureException` / `JwtException`. El filtro no autentica la peticion y el
 *   `RestAuthenticationEntryPoint` responde `401 Unauthorized`. La app Android al recibir 401
 *   borra el token de `AlmacenSeguro` y redirige al inicio de sesion.
 */
@Service
class JwtService(
    @Value("\${app.jwt.secret}") secretoBase64: String,
    @Value("\${app.jwt.expiracion-minutos:60}") private val expiracionMinutos: Long
) {
    private val clave: SecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretoBase64))

    fun generarToken(usuario: Usuario): String {
        val ahora = Instant.now()
        val expira = ahora.plus(expiracionMinutos, ChronoUnit.MINUTES)
        val privilegios = PrivilegiosPorRol.nombresDePrivilegios(usuario.rol.nombre).toList()

        return Jwts.builder()
            .subject(usuario.username)
            .claim("usuarioId", usuario.id)
            .claim("nombre", usuario.nombreCompleto)
            .claim("rol", usuario.rol.nombre)
            .claim("privilegios", privilegios)
            .issuedAt(Date.from(ahora))
            .expiration(Date.from(expira))
            .signWith(clave)
            .compact()
    }

    fun extraerUsername(token: String): String = leerClaims(token).subject

    fun leerClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(clave)
            .build()
            .parseSignedClaims(token)
            .payload

    fun getExpiracionSegundos(): Long = expiracionMinutos * 60
}
