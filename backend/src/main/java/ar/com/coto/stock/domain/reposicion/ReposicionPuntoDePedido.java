package ar.com.coto.stock.domain.reposicion;

import ar.com.coto.stock.domain.model.Reglas;
import ar.com.coto.stock.domain.model.Stock;
import java.util.Objects;

/** Almacén: al llegar al punto de pedido, completa hasta el stock objetivo. */
public final class ReposicionPuntoDePedido implements EstrategiaReposicion {
    private final int puntoPedido;
    private final int stockObjetivo;

    public ReposicionPuntoDePedido(int puntoPedido, int stockObjetivo) {
        this.puntoPedido = Reglas.positivo(puntoPedido, "puntoPedido");
        this.stockObjetivo = Reglas.positivo(stockObjetivo, "stockObjetivo");
        if (stockObjetivo <= puntoPedido) {
            throw new IllegalArgumentException("El objetivo debe superar el punto de pedido");
        }
    }

    @Override public int calcularCantidad(Stock s) {
        int disponible = Objects.requireNonNull(s).getDisponible();
        return disponible <= puntoPedido ? stockObjetivo - disponible : 0;
    }
}
