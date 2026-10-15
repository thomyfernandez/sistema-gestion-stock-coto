package ar.com.coto.stock.repository.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Modelo exclusivamente relacional; nunca se expone como entidad del dominio. */
@Entity
@Table(name = "stock")
public class StockEntity {
    @Id public Integer id;
    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id") public ProductoEntity producto;
    @Column(nullable = false, length = 6) public String tipo;
    public Integer stockFisico;
    public Integer stockExhibicion;
    public Integer stockReservado;
    @Version @Column(nullable = false) public long version;
    public StockEntity() { }
}
