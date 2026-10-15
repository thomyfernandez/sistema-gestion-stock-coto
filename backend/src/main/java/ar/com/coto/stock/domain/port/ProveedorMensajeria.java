package ar.com.coto.stock.domain.port;

/** Target de Adapter. El canal se configura en cada adaptador de la etapa 2. */
@FunctionalInterface
public interface ProveedorMensajeria {
    void enviarMensaje(String destino, String texto);
}
