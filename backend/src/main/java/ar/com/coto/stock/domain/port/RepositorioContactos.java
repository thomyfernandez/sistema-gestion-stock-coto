package ar.com.coto.stock.domain.port;
import ar.com.coto.stock.domain.model.Contacto;
import java.util.List;
import java.util.Optional;
public interface RepositorioContactos {
    List<Contacto> listar();
    Optional<Contacto> obtener(long id);
    Contacto guardar(Contacto contacto);
}
