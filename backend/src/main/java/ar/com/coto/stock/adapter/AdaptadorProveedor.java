package ar.com.coto.stock.adapter;

import ar.com.coto.stock.domain.port.ProveedorMensajeria;
import ar.com.coto.stock.domain.model.Reglas;
import java.util.Objects;

/** Adapter: enviarMensaje(destino,texto) -> send(canal,to,body). */
public class AdaptadorProveedor implements ProveedorMensajeria {
    private final ApiProveedorExterno api;
    private final String canal;
    public AdaptadorProveedor(ApiProveedorExterno api, String canal) {
        this.api = Objects.requireNonNull(api);
        this.canal = Reglas.texto(canal, "canal");
        if (!java.util.Set.of("EMAIL", "SMS", "WHATSAPP").contains(canal)) throw new IllegalArgumentException("Canal desconocido");
    }
    @Override public void enviarMensaje(String destino, String texto) { api.send(canal, destino, texto); }
}
