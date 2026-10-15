package ar.com.coto.stock.adapter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Adaptee simulado. Firma deliberadamente incompatible con ProveedorMensajeria. */
public class ApiProveedorExterno {
    public record Mensaje(String canal, String to, String body) { }
    private final List<Mensaje> mensajes = new CopyOnWriteArrayList<>();
    private volatile boolean disponible = true;
    public void send(String canal, String to, String body) {
        if (!disponible) throw new IllegalStateException("Proveedor de mensajería no disponible");
        mensajes.add(new Mensaje(canal, to, body));
    }
    public List<Mensaje> getMensajes() { return List.copyOf(mensajes); }
    public void setDisponible(boolean valor) { disponible = valor; }
    public boolean isDisponible() { return disponible; }
    public void limpiar() { mensajes.clear(); }
}
