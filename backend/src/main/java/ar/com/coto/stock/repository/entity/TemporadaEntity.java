package ar.com.coto.stock.repository.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/** Temporada de reposición: artículos en temporada_articulo o una categoría, nunca ambos. */
@Entity
@Table(name = "temporada_reposicion")
public class TemporadaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 80) public String nombre;
    public Integer categoriaId;
    /** tipo_stock tiene DEFAULT 'SIMPLE' y la FK compuesta a stock impide cargar combos. */
    @ElementCollection
    @CollectionTable(name = "temporada_articulo", joinColumns = @JoinColumn(name = "temporada_id"))
    @Column(name = "stock_id", nullable = false)
    public Set<Integer> stockIds = new LinkedHashSet<>();
    @Column(nullable = false) public int desdeMes;
    @Column(nullable = false) public int desdeDia;
    @Column(nullable = false) public int hastaMes;
    @Column(nullable = false) public int hastaDia;
    @Column(nullable = false, precision = 6, scale = 4) public BigDecimal factor;
    public TemporadaEntity() { }
}
