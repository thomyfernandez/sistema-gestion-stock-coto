package ar.com.coto.stock.adapter;

import ar.com.coto.stock.domain.port.ProveedorMensajeria;
import ar.com.coto.stock.domain.model.Canal;
import java.util.Objects;

/** Adapter: enviarMensaje(destino,texto) -> send(canal,to,body). */
public class AdaptadorProveedor implements ProveedorMensajeria {
    private final ApiProveedorExterno api;
    private final Canal canal;
    public AdaptadorProveedor(ApiProveedorExterno api, Canal canal) {
        this.api = Objects.requireNonNull(api);
        this.canal = Objects.requireNonNull(canal, "canal");
    }
    @Override public void enviarMensaje(String destino, String texto) { api.send(canal.name(), destino, texto); }
}
