package ar.com.coto.stock.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Mensaje de la outbox. La clave única evita encolados duplicados, no promete entrega exactamente una vez. */
public record EventoIntegracion(long id, String clave, Tipo tipo, int stockId, Long pedidoId,
                                Integer disponible, String canal, String destino, String texto, Instant fecha) {
    public enum Tipo { STOCK_DIGITAL, ALERTA, PEDIDO_ERP }
    public EventoIntegracion {
        clave = Reglas.texto(clave, "clave");
        Objects.requireNonNull(tipo);
        Reglas.positivo(stockId, "stockId");
        Objects.requireNonNull(fecha);
        switch (tipo) {
            case STOCK_DIGITAL -> Reglas.noNegativo(Objects.requireNonNull(disponible), "disponible");
            case ALERTA -> {
                canal = Reglas.texto(canal, "canal");
                destino = Reglas.texto(destino, "destino");
                texto = Reglas.texto(texto, "texto");
            }
            case PEDIDO_ERP -> {
                if (pedidoId == null || pedidoId <= 0) throw new IllegalArgumentException("Pedido obligatorio");
            }
        }
    }
}
