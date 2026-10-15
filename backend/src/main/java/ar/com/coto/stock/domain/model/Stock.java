package ar.com.coto.stock.domain.model;

import ar.com.coto.stock.domain.observer.ObservadorStock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Component del Composite y sujeto del Observer. No tiene anotaciones JPA/Spring. */
public abstract class Stock {
    /* Un monitor compartido mantiene atómicas las operaciones sobre varios componentes
       dentro de una JVM. Las transacciones/bloqueos SQL corresponden a la etapa 2. */
    protected static final Object MONITOR = new Object();
    protected final Producto producto;
    protected final List<ObservadorStock> observadores = new ArrayList<>();

    protected Stock(Producto producto) { this.producto = Objects.requireNonNull(producto); }

    public final Producto getProducto() { return producto; }
    public abstract int getDisponible();
    public abstract void reservar(int cant);
    public abstract void liberarReserva(int cant);
    public abstract void despacharReserva(int cant);

    public final void agregarObservador(ObservadorStock o) {
        synchronized (MONITOR) {
            Objects.requireNonNull(o);
            if (!observadores.contains(o)) observadores.add(o);
        }
    }

    public final void quitarObservador(ObservadorStock o) {
        synchronized (MONITOR) { observadores.remove(o); }
    }

    protected final void notificar() {
        synchronized (MONITOR) {
            for (ObservadorStock observador : List.copyOf(observadores)) {
                try {
                    observador.actualizar(this);
                } catch (RuntimeException ex) {
                    // Una integración fallida no revierte una mutación ya confirmada
                    // ni impide avisar al resto. La entrega durable se aborda en etapa 2.
                    System.getLogger(Stock.class.getName()).log(System.Logger.Level.WARNING,
                            "Falló un observador del producto " + producto.getId(), ex);
                }
            }
        }
    }
}
