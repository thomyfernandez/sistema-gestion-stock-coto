package ar.com.coto.stock.repository.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Modelo exclusivamente relacional; nunca se expone como entidad del dominio. */
@Entity
@Table(name = "bloqueo_operacion")
public class BloqueoEntity {
    @Id public Integer id;
    public BloqueoEntity() { }
}
