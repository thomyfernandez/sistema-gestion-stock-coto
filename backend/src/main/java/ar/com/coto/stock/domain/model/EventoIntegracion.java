package ar.com.coto.stock.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Mensaje de la outbox. La clave única evita encolados duplicados, no promete entrega exactamente una vez. */
public record EventoIntegracion(long id, String clave, Tipo tipo, int stockId, Long pedidoId,
                                Integer disponible, Canal canal, String destino, String texto, Instant fecha) {
    public enum Tipo { STOCK_DIGITAL, ALERTA, PEDIDO_ERP }

    public static EventoIntegracion stockDigital(String clave, int stockId, int disponible, Instant fecha) {
        return new EventoIntegracion(0, clave, Tipo.STOCK_DIGITAL, stockId, null, disponible, null, null, null, fecha);
    }
    public static EventoIntegracion alerta(String clave, int stockId, Canal canal, String destino, String texto, Instant fecha) {
        return new EventoIntegracion(0, clave, Tipo.ALERTA, stockId, null, null, canal, destino, texto, fecha);
    }
    /** La clave deriva del pedido: un mismo pedido nunca se envía dos veces al ERP. */
    public static EventoIntegracion pedidoErp(long pedidoId, int stockId, Instant fecha) {
        return new EventoIntegracion(0, "pedido:" + pedidoId, Tipo.PEDIDO_ERP, stockId, pedidoId, null, null, null, null, fecha);
    }
    public EventoIntegracion {
        clave = Reglas.texto(clave, "clave");
        Objects.requireNonNull(tipo);
        Reglas.positivo(stockId, "stockId");
        Objects.requireNonNull(fecha);
        switch (tipo) {
            case STOCK_DIGITAL -> Reglas.noNegativo(Objects.requireNonNull(disponible), "disponible");
            case ALERTA -> {
                Objects.requireNonNull(canal, "canal");
                destino = Reglas.texto(destino, "destino");
                texto = Reglas.texto(texto, "texto");
            }
            case PEDIDO_ERP -> {
                if (pedidoId == null || pedidoId <= 0) throw new IllegalArgumentException("Pedido obligatorio");
            }
        }
    }
}
