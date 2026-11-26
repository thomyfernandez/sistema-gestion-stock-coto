package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.valorizacion.Promocion;
import java.util.List;
import java.util.Map;

public interface RepositorioPromociones {
    List<Promocion> listar(int productoId);
    /** Todas las promociones agrupadas por producto, en el orden de aplicación. */
    Map<Integer, List<Promocion>> listarPorProducto();
    Promocion guardar(Promocion promocion);
    void eliminar(long id);
}
