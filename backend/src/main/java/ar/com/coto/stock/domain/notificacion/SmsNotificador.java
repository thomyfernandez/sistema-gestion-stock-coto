package ar.com.coto.stock.domain.notificacion;

import ar.com.coto.stock.domain.model.Reglas;
import ar.com.coto.stock.domain.port.ProveedorMensajeria;
import java.util.Objects;

/** Estrategia SMS. Recibe un proveedor configurado para SMS. */
public final class SmsNotificador implements CanalNotificador {
    private final ProveedorMensajeria proveedor;

    public SmsNotificador(ProveedorMensajeria proveedor) { this.proveedor = Objects.requireNonNull(proveedor); }

    @Override public void enviar(String destino, String texto) {
        proveedor.enviarMensaje(Reglas.texto(destino, "destino"), Reglas.texto(texto, "texto"));
    }
}
