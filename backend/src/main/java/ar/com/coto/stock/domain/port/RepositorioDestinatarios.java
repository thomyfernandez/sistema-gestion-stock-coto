package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.model.DestinatarioAviso;
import java.util.List;

/**
 * Puerto de persistencia para listas de destinatarios de avisos por correo electrónico.
 * No importa JPA, Spring ni SQL (regla arquitectónica del dominio).
 */
public interface RepositorioDestinatarios {
    List<DestinatarioAviso> listarGenerales();
    List<DestinatarioAviso> listarPorCategoria(int categoriaId);
    List<DestinatarioAviso> listarTodos();
    void guardarGenerales(List<String> emails);
    void guardarPorCategoria(int categoriaId, List<String> emails);
}
