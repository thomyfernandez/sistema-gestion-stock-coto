package ar.com.coto.stock.domain.reposicion;

import ar.com.coto.stock.domain.model.Canal;
import ar.com.coto.stock.domain.model.Reglas;
import java.time.Clock;
import java.util.Objects;

/** Configuración persistible; la creación de la estrategia permanece explícita.
 * El factor de la política estacional no se guarda acá: sale de las temporadas del artículo o de su categoría. */
public record PoliticaReposicion(int stockId, Tipo tipo, int puntoPedido, int stockObjetivo,
                                 double demandaDiaria, int diasCobertura,
                                 int umbralAlerta, String destinoAlerta, Long contactoId, Canal canalAlerta) {
    public PoliticaReposicion(int stockId, Tipo tipo, int puntoPedido, int stockObjetivo,
                              double demandaDiaria, int diasCobertura,
                              int umbralAlerta, String destinoAlerta) {
        this(stockId, tipo, puntoPedido, stockObjetivo, demandaDiaria, diasCobertura, umbralAlerta, destinoAlerta, null, null);
    }
    public enum Tipo { PUNTO_PEDIDO, DEMANDA, ESTACIONAL }
    public PoliticaReposicion {
        Reglas.positivo(stockId, "stockId");
        Objects.requireNonNull(tipo);
        Reglas.noNegativo(puntoPedido, "puntoPedido");
        Reglas.positivo(stockObjetivo, "stockObjetivo");
        Reglas.noNegativo(demandaDiaria, "demandaDiaria");
        Reglas.positivo(diasCobertura, "diasCobertura");
        Reglas.positivo(umbralAlerta, "umbralAlerta");
        destinoAlerta = Reglas.texto(destinoAlerta, "destinoAlerta");
        if (contactoId == null && canalAlerta != null) throw new IllegalArgumentException("Seleccioná un contacto para el aviso particular");
        if (contactoId != null && (contactoId <= 0 || canalAlerta == null))
            throw new IllegalArgumentException("Seleccioná un contacto y un canal válidos");
        switch (tipo) {
            case PUNTO_PEDIDO -> new ReposicionPuntoDePedido(puntoPedido, stockObjetivo);
            case DEMANDA -> new ReposicionPorDemanda(demandaDiaria, diasCobertura);
            // Con el factor máximo de una temporada, el objetivo tiene que seguir entrando en un int.
            case ESTACIONAL -> new ReposicionEstacional(stockObjetivo, Temporada.FACTOR_MAXIMO.doubleValue());
        }
    }
    /** @param temporadas las del artículo y su categoría; sólo las usa la política estacional.
     *  @param reloj de él sale la fecha con la que se elige la temporada vigente. */
    public EstrategiaReposicion crearEstrategia(CalendarioTemporadas temporadas, Clock reloj) {
        return switch (tipo) {
            case PUNTO_PEDIDO -> new ReposicionPuntoDePedido(puntoPedido, stockObjetivo);
            case DEMANDA -> new ReposicionPorDemanda(demandaDiaria, diasCobertura);
            case ESTACIONAL -> new ReposicionEstacional(stockObjetivo, temporadas, reloj);
        };
    }
}
