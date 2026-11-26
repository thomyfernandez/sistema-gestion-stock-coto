package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.model.*;
import java.util.Map;
import java.util.Optional;

public interface RepositorioStock {
    void bloquearInventario();
    Map<Integer, Stock> cargarInventario();
    /** Tipo del stock sin hidratar el grafo; vacío si no existe. */
    Optional<Stock.Tipo> tipo(int id);
    /** Datos de catálogo sin hidratar el grafo; vacío si no existe. */
    Optional<Producto> producto(int id);
    void guardarEstado(Stock stock);
    Stock crear(AltaProducto producto, boolean combo);
    void guardarCategoria(int productoId, Integer categoriaId);
    void guardarComposicion(ComboStock combo);
    Optional<ImagenProducto> consultarImagen(int productoId);
}
