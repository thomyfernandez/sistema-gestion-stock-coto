package ar.com.coto.stock.repository.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Modelo exclusivamente relacional; nunca se expone como entidad del dominio. */
@Entity
@Table(name = "producto")
public class ProductoEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Integer id;
    @Column(nullable = false, length = 160) public String nombre;
    @Column(nullable = false, length = 500) public String descripcion;
    @Column(nullable = false, precision = 15, scale = 2) public BigDecimal precio;
    public ProductoEntity() { }
}
