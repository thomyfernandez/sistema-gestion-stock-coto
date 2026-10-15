package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.valorizacion.Promocion;
import java.util.List;

public interface RepositorioPromociones {
    List<Promocion> listar(int productoId);
    Promocion guardar(Promocion promocion);
}
