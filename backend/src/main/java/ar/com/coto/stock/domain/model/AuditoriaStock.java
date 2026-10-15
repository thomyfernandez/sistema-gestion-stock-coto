package ar.com.coto.stock.domain.model;

import java.time.Instant;
import java.util.Objects;

/** El primer valor anterior es null: el observador aún no conocía ese stock. */
public record AuditoriaStock(int stockId, Instant fecha, Integer disponibleAnterior,
                            int disponibleActual, String detalle) {
    public AuditoriaStock {
        Reglas.positivo(stockId, "stockId");
        Objects.requireNonNull(fecha);
        if (disponibleAnterior != null) Reglas.noNegativo(disponibleAnterior, "disponibleAnterior");
        Reglas.noNegativo(disponibleActual, "disponibleActual");
        detalle = Reglas.texto(detalle, "detalle");
    }
}
