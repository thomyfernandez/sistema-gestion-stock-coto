package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.model.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RepositorioMovimientos {
    long guardar(MovimientoStock movimiento);
    List<MovimientoRegistrado> listar();
    Optional<MovimientoRegistrado> obtener(long id);
    /** Del más reciente al más antiguo. Filtros nulos o vacíos no restringen. */
    Pagina<MovimientoRegistrado> buscar(Integer stockId, Set<TipoMovimiento> tipos, int pagina, int limite);
}
