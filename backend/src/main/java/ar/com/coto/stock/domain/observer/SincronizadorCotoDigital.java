package ar.com.coto.stock.domain.observer;

import ar.com.coto.stock.domain.model.Stock;
import ar.com.coto.stock.domain.port.PublicadorDisponibilidad;
import java.util.Objects;

public final class SincronizadorCotoDigital implements ObservadorStock {
    private final PublicadorDisponibilidad publicador;

    public SincronizadorCotoDigital(PublicadorDisponibilidad publicador) {
        this.publicador = Objects.requireNonNull(publicador);
    }

    @Override public void actualizar(Stock s) {
        Objects.requireNonNull(s);
        publicador.sincronizar(s.getProducto().getId(), s.getDisponible());
    }
}
