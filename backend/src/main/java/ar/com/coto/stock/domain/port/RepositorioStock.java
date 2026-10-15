package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.model.*;
import java.util.Map;

public interface RepositorioStock {
    void bloquearInventario();
    Map<Integer, Stock> cargarInventario();
    void guardarEstado(StockSimple stock);
    Stock crear(AltaProducto producto, boolean combo);
    void guardarComposicion(ComboStock combo);
}
