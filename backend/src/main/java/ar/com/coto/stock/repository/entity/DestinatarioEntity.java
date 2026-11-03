package ar.com.coto.stock.repository.entity;
import jakarta.persistence.*;
/** Una dirección de una lista de avisos; categoría nula = lista general de la sucursal. */
@Entity @Table(name = "destinatario_aviso")
public class DestinatarioEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Integer categoriaId;
    @Column(nullable = false, length = 200) public String email;
}
