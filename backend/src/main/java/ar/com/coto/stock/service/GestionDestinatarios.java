package ar.com.coto.stock.service;
import ar.com.coto.stock.domain.model.ListaDestinatarios;
import java.util.List;
public interface GestionDestinatarios {
    List<ListaDestinatarios> listar();
    ListaDestinatarios guardar(ListaDestinatarios lista);
}
