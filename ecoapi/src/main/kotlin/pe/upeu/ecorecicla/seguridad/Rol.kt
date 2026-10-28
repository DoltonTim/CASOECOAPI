package pe.upeu.ecorecicla.seguridad

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Entidad JPA que representa un Rol en la tabla `roles` de Oracle.
 */
@Entity
@Table(name = "roles")
class Rol(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 30)
    var nombre: String = "",

    @Column(length = 150)
    var descripcion: String? = null
)
