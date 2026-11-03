package ar.com.coto.stock.service;

import java.util.List;
import java.util.Map;

/**
 * Contrato de servicio para la administración y resolución jerárquica de destinatarios de avisos.
 */
public interface GestionDestinatarios {
    List<String> listarGenerales();
    Map<Integer, List<String>> listarPorCategoria();
    List<String> resolverDestinatarios(int productoId);
    void guardarGenerales(List<String> emails);
    void guardarPorCategoria(int categoriaId, List<String> emails);
}
