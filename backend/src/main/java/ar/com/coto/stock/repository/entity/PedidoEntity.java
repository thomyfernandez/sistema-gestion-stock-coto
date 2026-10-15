package ar.com.coto.stock.repository.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Modelo exclusivamente relacional; nunca se expone como entidad del dominio. */
@Entity
@Table(name = "pedido_reposicion")
public class PedidoEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public int stockId;
    @Column(nullable = false, length = 6) public String tipoStock = "SIMPLE";
    @Column(nullable = false) public Instant fecha;
    @Column(nullable = false) public int cantidad;
    @Column(nullable = false, length = 10) public String estado;
    public PedidoEntity() { }
}
