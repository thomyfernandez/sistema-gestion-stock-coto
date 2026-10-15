package ar.com.coto.stock.domain.observer;

import ar.com.coto.stock.domain.model.Stock;

/** Observer: recibe el stock una vez confirmado el cambio en memoria. */
@FunctionalInterface
public interface ObservadorStock {
    void actualizar(Stock s);
}
