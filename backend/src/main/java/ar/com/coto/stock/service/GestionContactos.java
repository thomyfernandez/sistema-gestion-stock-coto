package ar.com.coto.stock.service;
import ar.com.coto.stock.domain.model.Contacto;
import java.util.List;
public interface GestionContactos {
    List<Contacto> listar();
    Contacto consultar(long id);
    Contacto guardar(Contacto contacto);
}
