package ar.com.coto.stock.repository.entity;

import jakarta.persistence.*;

/** Entidad JPA de usuario. Exclusiva de persistencia; no contamina el dominio. */
@Entity
@Table(name = "usuario")
public class UsuarioEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 60)
    public String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    public String passwordHash;

    @Column(name = "nombre_completo", nullable = false, length = 160)
    public String nombreCompleto;

    @Column(nullable = false, length = 30)
    public String rol;

    @Column(nullable = false)
    public boolean activo = true;

    public UsuarioEntity() { }

    public UsuarioEntity(Long id, String username, String passwordHash, String nombreCompleto, String rol, boolean activo) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.nombreCompleto = nombreCompleto;
        this.rol = rol;
        this.activo = activo;
    }
}

