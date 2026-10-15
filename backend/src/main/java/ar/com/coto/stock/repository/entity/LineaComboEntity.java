package ar.com.coto.stock.repository.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Modelo exclusivamente relacional; nunca se expone como entidad del dominio. */
@Entity
@Table(name = "linea_combo")
public class LineaComboEntity {
    @EmbeddedId public LineaComboId id;
    @Column(nullable = false, length = 6) public String tipoCombo = "COMBO";
    @Column(nullable = false) public int cantidadEnCombo;
    public LineaComboEntity() { }
}
