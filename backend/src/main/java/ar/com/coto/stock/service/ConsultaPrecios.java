package ar.com.coto.stock.service;

import java.math.BigDecimal;
import java.util.List;

public interface ConsultaPrecios {
    record PrecioCalculado(int productoId, int cantidad, BigDecimal precioLista,
                          BigDecimal precioFinal, BigDecimal ahorro, List<Long> promocionesAplicadas) {
        public PrecioCalculado { promocionesAplicadas = List.copyOf(promocionesAplicadas); }
    }
    record ItemCatalogo(int id, String nombre, String descripcion, String tipo, int disponible, PrecioCalculado precio) { }
    PrecioCalculado valorizar(int productoId, int cantidad, String medioPago);
    List<ItemCatalogo> catalogo(int cantidad, String medioPago);
}
