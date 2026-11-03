package ar.com.coto.stock.domain.port;
import ar.com.coto.stock.domain.model.ListaDestinatarios;
import java.util.List;
public interface RepositorioDestinatarios {
    /** La lista general primero y luego las categorías con al menos un email. */
    List<ListaDestinatarios> listar();
    ListaDestinatarios obtener(Integer categoriaId);
    void reemplazar(ListaDestinatarios lista);
}
