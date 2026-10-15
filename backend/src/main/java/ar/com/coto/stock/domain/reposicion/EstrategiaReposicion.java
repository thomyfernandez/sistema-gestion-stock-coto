package ar.com.coto.stock.domain.reposicion;

import ar.com.coto.stock.domain.model.Stock;

/** Strategy: retorna unidades a pedir; cero significa que no se necesita un pedido. */
@FunctionalInterface
public interface EstrategiaReposicion {
    int calcularCantidad(Stock s);
}
