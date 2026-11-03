package ar.com.coto.stock.domain.reposicion;

import ar.com.coto.stock.domain.model.Reglas;
import java.util.Objects;

/** Configuración persistible; la creación de la estrategia permanece explícita. */
public record PoliticaReposicion(int stockId, Tipo tipo, int puntoPedido, int stockObjetivo,
                                 double demandaDiaria, int diasCobertura, double factorEstacional,
                                 int umbralAlerta, String destinoAlerta, Long contactoId, String canalAlerta) {
    public PoliticaReposicion(int stockId, Tipo tipo, int puntoPedido, int stockObjetivo,
                              double demandaDiaria, int diasCobertura, double factorEstacional,
                              int umbralAlerta, String destinoAlerta) {
        this(stockId, tipo, puntoPedido, stockObjetivo, demandaDiaria, diasCobertura, factorEstacional, umbralAlerta, destinoAlerta, null, null);
    }
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
        if (contactoId == null && canalAlerta != null) throw new IllegalArgumentException("Seleccioná un contacto para el aviso particular");
        if (contactoId != null && (contactoId <= 0 || canalAlerta == null
                || !java.util.Set.of("EMAIL", "SMS", "WHATSAPP").contains(canalAlerta)))
            throw new IllegalArgumentException("Seleccioná un contacto y un canal válidos");
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
