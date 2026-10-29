package ar.com.coto.stock.domain.port;
import ar.com.coto.stock.domain.model.Categoria;
import java.util.List;
import java.util.Optional;
public interface RepositorioCategorias {
    List<Categoria> listar();
    Optional<Categoria> obtener(int id);
    Categoria guardar(Categoria categoria);
}
