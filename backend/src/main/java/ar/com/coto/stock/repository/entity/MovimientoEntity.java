package ar.com.coto.stock.repository.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Modelo exclusivamente relacional; nunca se expone como entidad del dominio. */
@Entity
@Table(name = "movimiento_stock")
public class MovimientoEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public int stockId;
    @Column(nullable = false, length = 30) public String tipo;
    @Column(nullable = false) public int cantidad;
    @Column(nullable = false) public Instant fecha;
    @Column(nullable = false, length = 500) public String motivo;
    @Column(nullable = false, length = 100) public String usuario;
    public MovimientoEntity() { }
}
