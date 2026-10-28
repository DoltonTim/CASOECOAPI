package pe.upeu.ecorecicla.seguridad

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import java.util.Optional

@Repository
interface UsuarioRepository : JpaRepository<Usuario, Long> {
    fun findByUsernameIgnoreCase(username: String): Optional<Usuario>
}

@Repository
interface RolRepository : JpaRepository<Rol, Long> {
    fun findByNombreIgnoreCase(nombre: String): Optional<Rol>
}

/**
 * Servicio que adapta la entidad [Usuario] al contrato [UserDetails] de Spring Security,
 * cargando sus privilegios desde [PrivilegiosPorRol].
 */
@Service
class UsuarioDetailsService(
    private val usuarioRepository: UsuarioRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val usuario = usuarioRepository.findByUsernameIgnoreCase(username)
            .orElseThrow { UsernameNotFoundException("Usuario no encontrado: $username") }

        return User.builder()
            .username(usuario.username)
            .password(usuario.password)
            .disabled(!usuario.estado)
            .authorities(PrivilegiosPorRol.authorities(usuario.rol.nombre))
            .build()
    }
}
