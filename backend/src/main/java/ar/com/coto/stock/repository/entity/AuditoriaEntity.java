package ar.com.coto.stock.repository.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Modelo exclusivamente relacional; nunca se expone como entidad del dominio. */
@Entity
@Table(name = "auditoria_stock")
public class AuditoriaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public int stockId;
    public Long movimientoId;
    @Column(nullable = false) public Instant fecha;
    public Integer disponibleAnterior;
    @Column(nullable = false) public int disponibleActual;
    @Column(nullable = false, length = 500) public String detalle;
    public AuditoriaEntity() { }
}
