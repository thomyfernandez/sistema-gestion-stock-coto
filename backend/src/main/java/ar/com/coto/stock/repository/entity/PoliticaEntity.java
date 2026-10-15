package ar.com.coto.stock.repository.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Modelo exclusivamente relacional; nunca se expone como entidad del dominio. */
@Entity
@Table(name = "politica_reposicion")
public class PoliticaEntity {
    @Id public Integer stockId;
    @Column(nullable = false, length = 6) public String tipoStock = "SIMPLE";
    @Column(nullable = false, length = 15) public String tipo;
    @Column(nullable = false) public int puntoPedido;
    @Column(nullable = false) public int stockObjetivo;
    @Column(nullable = false, precision = 12, scale = 3) public BigDecimal demandaDiaria;
    @Column(nullable = false) public int diasCobertura;
    @Column(nullable = false, precision = 12, scale = 4) public BigDecimal factorEstacional;
    @Column(nullable = false) public int umbralAlerta;
    @Column(nullable = false, length = 200) public String destinoAlerta;
    @Column(nullable = false) public boolean alertaActiva;
    public PoliticaEntity() { }
}
