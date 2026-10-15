package ar.com.coto.stock.adapter;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Simula la API incompatible del ERP. La referencia de pedido es idempotente. */
public class SistemaERPExterno {
    public record Orden(String reference, int sku, int units, String date) { }
    private final Map<String, Orden> ordenes = new ConcurrentHashMap<>();
    private volatile boolean disponible = true;
    public void submitPurchaseOrder(String reference, int sku, int units, String date) {
        if (!disponible) throw new IllegalStateException("ERP no disponible");
        ordenes.putIfAbsent(reference, new Orden(reference, sku, units, date));
    }
    public List<Orden> getOrdenes() { return ordenes.values().stream().sorted(java.util.Comparator.comparing(Orden::reference)).toList(); }
    public void setDisponible(boolean valor) { disponible = valor; }
    public boolean isDisponible() { return disponible; }
    public void limpiar() { ordenes.clear(); }
}
