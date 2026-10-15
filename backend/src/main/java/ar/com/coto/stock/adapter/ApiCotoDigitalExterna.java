package ar.com.coto.stock.adapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ApiCotoDigitalExterna {
    private final Map<String, String> inventario = new ConcurrentHashMap<>();
    private volatile boolean disponible = true;
    public void updateInventory(String sku, String units) {
        if (!disponible) throw new IllegalStateException("Coto Digital no disponible");
        inventario.put(sku, units);
    }
    public Map<String, String> getInventario() { return Map.copyOf(inventario); }
    public void setDisponible(boolean valor) { disponible = valor; }
    public boolean isDisponible() { return disponible; }
    public void limpiar() { inventario.clear(); }
}
