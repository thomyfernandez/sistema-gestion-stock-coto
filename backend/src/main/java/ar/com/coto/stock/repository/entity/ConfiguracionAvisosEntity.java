package ar.com.coto.stock.repository.entity;
import jakarta.persistence.*;
@Entity @Table(name = "configuracion_avisos")
public class ConfiguracionAvisosEntity {
    @Id public int id = 1;
    @Column(nullable = false, length = 15) public String canal;
    @Column(nullable = false, length = 200) public String email;
    @Column(nullable = false, length = 30) public String telefono;
    @Version public long version;
}
