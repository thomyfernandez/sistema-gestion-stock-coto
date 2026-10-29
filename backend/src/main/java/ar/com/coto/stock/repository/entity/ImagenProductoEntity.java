package ar.com.coto.stock.repository.entity;
import jakarta.persistence.*;
@Entity @Table(name = "imagen_producto")
public class ImagenProductoEntity {
    @Id public int productoId;
    @Column(nullable = false, length = 30) public String tipo;
    @Column(nullable = false, columnDefinition = "bytea") public byte[] contenido;
}
