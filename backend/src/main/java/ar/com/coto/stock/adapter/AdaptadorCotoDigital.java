package ar.com.coto.stock.adapter;

import ar.com.coto.stock.domain.port.PublicadorDisponibilidad;
import java.util.Objects;

public final class AdaptadorCotoDigital implements PublicadorDisponibilidad {
    private final ApiCotoDigitalExterna api;
    public AdaptadorCotoDigital(ApiCotoDigitalExterna api) { this.api = Objects.requireNonNull(api); }
    @Override public void sincronizar(int productoId, int disponible) {
        api.updateInventory(Integer.toString(productoId), Integer.toString(disponible));
    }
}
