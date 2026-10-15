package ar.com.coto.stock.domain.observer;

import ar.com.coto.stock.domain.model.Reglas;
import ar.com.coto.stock.domain.model.Stock;
import ar.com.coto.stock.domain.port.EmisorAlertas;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** ConcreteObserver: avisa una vez por episodio de stock bajo y se rearma al recuperarse. */
public final class AlertaStockBajo implements ObservadorStock {
    private final int umbral;
    private final String destino;
    private final EmisorAlertas emisor;
    private final Map<Integer, Boolean> alertados = new HashMap<>();
    private Aviso ultimoAviso;

    public AlertaStockBajo(int umbral, String destino, EmisorAlertas emisor) {
        this.umbral = Reglas.positivo(umbral, "umbral");
        this.destino = Reglas.texto(destino, "destino");
        this.emisor = Objects.requireNonNull(emisor);
    }

    @Override public synchronized void actualizar(Stock s) {
        Objects.requireNonNull(s);
        int id = s.getProducto().getId();
        int disponible = s.getDisponible();
        if (disponible >= umbral) {
            alertados.remove(id);
        } else if (!alertados.getOrDefault(id, false)) {
            ultimoAviso = new Aviso(id, s.getProducto().getNombre(), disponible, umbral, destino);
            emisor.enviar(this);
            // Si el emisor falla, el próximo cambio permite reintentar el aviso.
            alertados.put(id, true);
        }
    }

    public int getUmbral() { return umbral; }
    public String getDestino() { return destino; }
    /** Restaura el episodio confirmado, sin emitir avisos durante la hidratación. */
    public synchronized void restaurarEstado(int productoId, boolean activa) {
        Reglas.positivo(productoId, "productoId");
        if (activa) alertados.put(productoId, true);
        else alertados.remove(productoId);
    }
    public synchronized Aviso getUltimoAviso() {
        if (ultimoAviso == null) throw new IllegalStateException("Todavía no se generó una alerta");
        return ultimoAviso;
    }

    /** El servicio debe capturar este valor inmutable antes de una entrega asíncrona. */
    public record Aviso(int productoId, String producto, int disponible, int umbral, String destino) {
        public String getTexto() {
            return "Stock bajo: " + producto + " (" + productoId + "). Disponible: "
                    + disponible + "; umbral: " + umbral + ".";
        }
    }
}
