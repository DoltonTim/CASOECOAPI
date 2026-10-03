package pe.upeu.ecorecicla.seguridad

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * Entidad JPA que representa un Usuario en la tabla `usuarios` de Oracle.
 * La contrasena se almacena cifrada con BCrypt (BCryptPasswordEncoder).
 */
@Entity
@Table(name = "usuarios")
class Usuario(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 50)
    var username: String = "",

    @Column(nullable = false, length = 100)
    var password: String = "",

    @Column(name = "nombre_completo", nullable = false, length = 120)
    var nombreCompleto: String = "",

    @Column(nullable = false)
    var estado: Boolean = true,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rol_id", nullable = false)
    var rol: Rol = Rol()
)
