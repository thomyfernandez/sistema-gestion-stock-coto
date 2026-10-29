package ar.com.coto.stock.repository.entity;
import jakarta.persistence.*;
@Entity @Table(name = "proveedor")
public class ContactoEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 160) public String nombre;
    @Column(nullable = false, length = 200) public String email;
    @Column(nullable = false, length = 30) public String telefono;
    @Version public long version;
}
