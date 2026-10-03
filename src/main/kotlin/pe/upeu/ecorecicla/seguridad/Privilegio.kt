package pe.upeu.ecorecicla.seguridad

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * ============================================================================
 * ENUM DE PRIVILEGIOS Y MATRIZ DE AUTORIZACION (SECCION 3.3 DEL EXAMEN)
 * ============================================================================
 * EXPLICACION PARA DEFENSA (Preguntas 1 y 12):
 * - El sistema define los permisos por PRIVILEGIO y NUNCA por el nombre del rol
 *   escrito con un 'if' dentro de un controlador.
 * - Cada rol es un conjunto inmutable de privilegios definido en [PrivilegiosPorRol].
 * - Para agregar un rol nuevo (como AUDITOR en SC-B) o un privilegio adicional,
 *   solo se declara aqui en la matriz centralizada sin tocar ni el filtro JWT
 *   ni los controladores REST.
 */
enum class Privilegio {
    /** Consultar la ruta del dia y el padron de puntos de acopio */
    PUNTO_LEER,

    /** Consultar los pesajes registrados en un punto de acopio */
    PESAJE_LEER,

    /** Registrar un pesaje nuevo en un punto de la ruta */
    PESAJE_CREAR,

    /** Corregir un pesaje existente (sujeto a RN-01 y RN-02) */
    PESAJE_EDITAR,

    /** Anular logicamente un pesaje marcandolo con motivo (solo supervisor) */
    PESAJE_ANULAR,

    /** Cerrar la ruta del dia cuando todos los puntos estan atendidos (RN-04) */
    RUTA_CERRAR,

    /** Crear puntos de acopio y gestionar rutas (solo administrador) */
    PUNTO_GESTIONAR
}

/**
 * Matriz oficial de privilegios por rol (Seccion 3.3 + SC-B Rol AUDITOR).
 */
object PrivilegiosPorRol {

    private val MATRIZ: Map<String, Set<Privilegio>> = mapOf(
        "OPERARIO" to setOf(
            Privilegio.PUNTO_LEER,
            Privilegio.PESAJE_LEER,
            Privilegio.PESAJE_CREAR,
            Privilegio.PESAJE_EDITAR
        ),
        "SUPERVISOR" to setOf(
            Privilegio.PUNTO_LEER,
            Privilegio.PESAJE_LEER,
            Privilegio.PESAJE_CREAR,
            Privilegio.PESAJE_EDITAR,
            Privilegio.PESAJE_ANULAR,
            Privilegio.RUTA_CERRAR
        ),
        "ADMINISTRADOR" to setOf(
            Privilegio.PUNTO_LEER,
            Privilegio.PESAJE_LEER,
            Privilegio.PUNTO_GESTIONAR
        ),
        "ADMIN" to setOf(
            Privilegio.PUNTO_LEER,
            Privilegio.PESAJE_LEER,
            Privilegio.PUNTO_GESTIONAR
        ),
        // SC-B: Rol AUDITOR con todos los privilegios de lectura y ninguno de escritura
        "AUDITOR" to setOf(
            Privilegio.PUNTO_LEER,
            Privilegio.PESAJE_LEER
        )
    )

    /** Devuelve los privilegios asociados al nombre del rol. */
    fun deRol(nombreRol: String): Set<Privilegio> =
        MATRIZ[nombreRol.uppercase()] ?: emptySet()

    /** Devuelve los nombres de los privilegios ordenados para incluirlos en el JWT y DTOs. */
    fun nombresDePrivilegios(nombreRol: String): Set<String> =
        deRol(nombreRol).map { it.name }.toSortedSet()

    /** Convierte los privilegios del rol en GrantedAuthority para Spring Security. */
    fun authorities(nombreRol: String): List<GrantedAuthority> {
        val resultado = mutableListOf<GrantedAuthority>()
        resultado.add(SimpleGrantedAuthority("ROLE_${nombreRol.uppercase()}"))
        nombresDePrivilegios(nombreRol).forEach { priv ->
            resultado.add(SimpleGrantedAuthority(priv))
        }
        return resultado
    }
}
