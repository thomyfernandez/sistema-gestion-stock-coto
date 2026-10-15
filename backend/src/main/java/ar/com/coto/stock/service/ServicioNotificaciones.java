package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.notificacion.CanalNotificador;
import ar.com.coto.stock.domain.observer.AlertaStockBajo;
import ar.com.coto.stock.domain.port.EmisorAlertas;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Context de Strategy. Los mensajes encolados conservan el canal elegido al crearse. */
public final class ServicioNotificaciones implements EmisorAlertas, GestionNotificaciones {
    private final Map<Canal, CanalNotificador> canales;
    private final AtomicReference<Canal> canal = new AtomicReference<>(Canal.EMAIL);
    public ServicioNotificaciones(Map<Canal, CanalNotificador> canales) {
        this.canales = Map.copyOf(canales);
        for (Canal c : Canal.values()) Objects.requireNonNull(this.canales.get(c), "Falta canal " + c);
    }
    public void setCanal(Canal nuevo) { canal.set(Objects.requireNonNull(nuevo)); }
    public Canal getCanal() { return canal.get(); }
    @Override public void enviar(AlertaStockBajo alerta) {
        var aviso = alerta.getUltimoAviso();
        enviar(canal.get(), aviso.destino(), aviso.getTexto());
    }
    public void enviar(Canal canal, String destino, String texto) { canales.get(Objects.requireNonNull(canal)).enviar(destino, texto); }
}
