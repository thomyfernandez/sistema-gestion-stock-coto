package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.reposicion.Temporada;
import java.util.List;
import java.util.Optional;

/** Temporadas de reposición por artículo o por categoría. */
public interface RepositorioTemporadas {
    /** Todas, primero las de artículo y luego las de categoría, cada grupo por fecha de inicio. */
    List<Temporada> listar();
    /** Las del artículo y las de su categoría (que puede ser nula). */
    List<Temporada> listarPara(int stockId, Integer categoriaId);
    Optional<Temporada> obtener(long id);
    /** Alta si el id es 0; si no, reemplaza la existente. Devuelve la temporada con su id. */
    Temporada guardar(Temporada temporada);
    void eliminar(long id);

    /** Sin temporadas: la política estacional se comporta con factor 1. */
    RepositorioTemporadas VACIO = new RepositorioTemporadas() {
        @Override public List<Temporada> listar() { return List.of(); }
        @Override public List<Temporada> listarPara(int stockId, Integer categoriaId) { return List.of(); }
        @Override public Optional<Temporada> obtener(long id) { return Optional.empty(); }
        @Override public Temporada guardar(Temporada temporada) { throw new UnsupportedOperationException("Repositorio sin persistencia"); }
        @Override public void eliminar(long id) { throw new UnsupportedOperationException("Repositorio sin persistencia"); }
    };
}
