package ar.com.coto.stock.repository.entity;
import jakarta.persistence.*;
@Entity @Table(name = "categoria")
public class CategoriaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Integer id;
    @Column(nullable = false, length = 80) public String nombre;
    @Column(nullable = false, unique = true, length = 80) public String nombreClave;
    public Long contactoId;
    @Column(length = 15) public String canalAlerta;
    @Version public long version;
}
