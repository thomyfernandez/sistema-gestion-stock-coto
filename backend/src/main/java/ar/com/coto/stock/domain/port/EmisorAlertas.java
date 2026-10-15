package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.observer.AlertaStockBajo;

/** Puerto que implementará ServicioNotificaciones en la etapa 2. */
@FunctionalInterface
public interface EmisorAlertas {
    void enviar(AlertaStockBajo alerta);
}
