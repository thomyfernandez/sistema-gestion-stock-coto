package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.model.*;
import java.util.List;

public interface RepositorioPedidos {
    PedidoRegistrado guardar(PedidoReposicion pedido);
    PedidoRegistrado obtener(long id);
    void actualizar(PedidoRegistrado pedido);
    int cantidadPendiente(int stockId);
    List<PedidoRegistrado> listar();
}
