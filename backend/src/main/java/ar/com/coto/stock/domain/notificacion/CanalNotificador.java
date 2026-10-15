package ar.com.coto.stock.domain.notificacion;

@FunctionalInterface
public interface CanalNotificador {
    void enviar(String destino, String texto);
}
