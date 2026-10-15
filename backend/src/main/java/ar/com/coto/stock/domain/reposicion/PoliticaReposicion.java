package ar.com.coto.stock.domain.reposicion;

import ar.com.coto.stock.domain.model.Reglas;
import java.util.Objects;

/** Configuración persistible; la creación de la estrategia permanece explícita. */
public record PoliticaReposicion(int stockId, Tipo tipo, int puntoPedido, int stockObjetivo,
                                 double demandaDiaria, int diasCobertura, double factorEstacional,
                                 int umbralAlerta, String destinoAlerta) {
    public enum Tipo { PUNTO_PEDIDO, DEMANDA, ESTACIONAL }
    public PoliticaReposicion {
        Reglas.positivo(stockId, "stockId");
        Objects.requireNonNull(tipo);
        Reglas.noNegativo(puntoPedido, "puntoPedido");
        Reglas.positivo(stockObjetivo, "stockObjetivo");
        Reglas.noNegativo(demandaDiaria, "demandaDiaria");
        Reglas.positivo(diasCobertura, "diasCobertura");
        Reglas.noNegativo(factorEstacional, "factorEstacional");
        if (factorEstacional == 0) throw new IllegalArgumentException("Factor debe ser positivo");
        Reglas.positivo(umbralAlerta, "umbralAlerta");
        destinoAlerta = Reglas.texto(destinoAlerta, "destinoAlerta");
        switch (tipo) {
            case PUNTO_PEDIDO -> new ReposicionPuntoDePedido(puntoPedido, stockObjetivo);
            case DEMANDA -> new ReposicionPorDemanda(demandaDiaria, diasCobertura);
            case ESTACIONAL -> new ReposicionEstacional(stockObjetivo, factorEstacional);
        }
    }
    public EstrategiaReposicion crearEstrategia() {
        return switch (tipo) {
            case PUNTO_PEDIDO -> new ReposicionPuntoDePedido(puntoPedido, stockObjetivo);
            case DEMANDA -> new ReposicionPorDemanda(demandaDiaria, diasCobertura);
            case ESTACIONAL -> new ReposicionEstacional(stockObjetivo, factorEstacional);
        };
    }
}
