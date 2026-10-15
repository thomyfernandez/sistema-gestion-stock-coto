package ar.com.coto.stock.domain.observer;

import ar.com.coto.stock.domain.model.AuditoriaStock;
import ar.com.coto.stock.domain.model.Stock;
import ar.com.coto.stock.domain.port.RegistroAuditoria;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Audita todo cambio notificado, incluso si el disponible conserva el mismo valor. */
public final class AuditorMovimientos implements ObservadorStock {
    private final RegistroAuditoria registro;
    private final Clock reloj;
    private final Map<Integer, Integer> anteriores = new HashMap<>();

    public AuditorMovimientos(RegistroAuditoria registro) { this(registro, Clock.systemUTC()); }

    public AuditorMovimientos(RegistroAuditoria registro, Clock reloj) {
        this.registro = Objects.requireNonNull(registro);
        this.reloj = Objects.requireNonNull(reloj);
    }

    public synchronized void establecerLineaBase(Stock stock) {
        anteriores.put(stock.getProducto().getId(), stock.getDisponible());
    }

    @Override public synchronized void actualizar(Stock s) {
        Objects.requireNonNull(s);
        int id = s.getProducto().getId();
        int disponible = s.getDisponible();
        registro.registrar(new AuditoriaStock(id, reloj.instant(), anteriores.get(id), disponible,
                "Cambio de stock de " + s.getProducto().getNombre()));
        anteriores.put(id, disponible);
    }
}
