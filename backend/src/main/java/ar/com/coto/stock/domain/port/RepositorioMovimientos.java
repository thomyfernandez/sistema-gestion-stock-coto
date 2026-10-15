package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.model.*;
import java.util.List;

public interface RepositorioMovimientos {
    long guardar(MovimientoStock movimiento);
    List<MovimientoRegistrado> listar();
}
