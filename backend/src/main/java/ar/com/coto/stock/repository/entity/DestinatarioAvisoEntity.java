package ar.com.coto.stock.repository.entity;

import jakarta.persistence.*;

/**
 * Entidad relacional para destinatarios de avisos de stock bajo.
 */
@Entity
@Table(name = "destinatario_aviso")
public class DestinatarioAvisoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "categoria_id")
    public Integer categoriaId;

    @Column(nullable = false, length = 200)
    public String email;

    public DestinatarioAvisoEntity() { }

    public DestinatarioAvisoEntity(Integer categoriaId, String email) {
        this.categoriaId = categoriaId;
        this.email = email;
    }
}
