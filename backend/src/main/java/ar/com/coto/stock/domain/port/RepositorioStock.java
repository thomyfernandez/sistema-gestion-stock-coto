package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.model.*;
import java.util.Map;

public interface RepositorioStock {
    void bloquearInventario();
    Map<Integer, Stock> cargarInventario();
    void guardarEstado(StockSimple stock);
    Stock crear(AltaProducto producto, boolean combo);
    void guardarCategoria(int productoId, Integer categoriaId);
    void guardarComposicion(ComboStock combo);
    java.util.Optional<ImagenProducto> consultarImagen(int productoId);
}
