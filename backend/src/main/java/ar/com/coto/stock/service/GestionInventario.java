package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.reposicion.PoliticaReposicion;
import ar.com.coto.stock.domain.valorizacion.Promocion;
import java.util.List;
import java.util.Map;

/** Contrato de aplicación para los futuros controladores REST. */
public interface GestionInventario {
    record ResultadoMovimiento(long id, Map<Integer, Integer> disponibilidades, List<PedidoRegistrado> pedidosGenerados) {
        public ResultadoMovimiento { disponibilidades = Map.copyOf(disponibilidades); pedidosGenerados = List.copyOf(pedidosGenerados); }
    }
    ResultadoMovimiento registrarMovimiento(MovimientoStock movimiento);
    List<PedidoRegistrado> evaluarReposicion();
    Stock consultarStock(int id);
    List<Stock> listarStock();
    ImagenProducto consultarImagen(int productoId);
    Stock crearProducto(AltaProducto datos);
    ComboStock crearCombo(AltaProducto datos, List<ComponenteCombo> componentes);
    void asignarCategoria(int productoId, Integer categoriaId);
    void configurarPolitica(PoliticaReposicion politica);
    Promocion guardarPromocion(Promocion promocion);
    List<Promocion> listarPromociones(int productoId);
    List<MovimientoRegistrado> listarMovimientos();
    List<AuditoriaStock> listarAuditoria();
    List<PedidoRegistrado> listarPedidos();
    ResultadoMovimiento recibirPedido(long id, String usuario);
    void cancelarPedido(long id);
    PoliticaReposicion consultarPolitica(int stockId);
    default MovimientoRegistrado consultarMovimiento(long id) {
        return listarMovimientos().stream().filter(m -> m.id() == id).findFirst()
                .orElseThrow(() -> new EntidadNoEncontradaException("Movimiento", id));
    }
    default PedidoRegistrado consultarPedido(long id) {
        return listarPedidos().stream().filter(p -> p.id() == id).findFirst()
                .orElseThrow(() -> new EntidadNoEncontradaException("Pedido", id));
    }
    default Promocion consultarPromocion(int productoId, long id) {
        return listarPromociones(productoId).stream().filter(p -> p.id() == id).findFirst()
                .orElseThrow(() -> new EntidadNoEncontradaException("Promoción", id));
    }
    default ComboStock consultarCombo(int id) {
        Stock stock = consultarStock(id);
        if (!(stock instanceof ComboStock combo)) throw new EntidadNoEncontradaException("Combo", id);
        return combo;
    }
}
