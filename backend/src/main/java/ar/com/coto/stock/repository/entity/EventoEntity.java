package ar.com.coto.stock.repository.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Modelo exclusivamente relacional; nunca se expone como entidad del dominio. */
@Entity
@Table(name = "evento_integracion")
public class EventoEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 150, unique = true) public String clave;
    @Column(nullable = false, length = 20) public String tipo;
    @Column(nullable = false) public int stockId;
    public Long pedidoId;
    public Integer disponible;
    @Column(length = 15) public String canal;
    @Column(length = 200) public String destino;
    @Column(length = 1000) public String texto;
    @Column(nullable = false) public Instant fecha;
    @Column(nullable = false, length = 10) public String estado = "PENDIENTE";
    @Column(nullable = false) public int intentos;
    @Column(length = 500) public String ultimoError;
    public EventoEntity() { }
}
