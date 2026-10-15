package ar.com.coto.stock.repository.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Modelo exclusivamente relacional; nunca se expone como entidad del dominio. */
@Entity
@Table(name = "promocion")
public class PromocionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public int productoId;
    @Column(nullable = false, length = 15) public String tipo;
    @Column(nullable = false, precision = 7, scale = 4) public BigDecimal porcentaje;
    @Column(nullable = false) public int orden;
    @Column(nullable = false) public boolean activa;
    @Column(length = 60) public String medioPago;
    public PromocionEntity() { }
}
