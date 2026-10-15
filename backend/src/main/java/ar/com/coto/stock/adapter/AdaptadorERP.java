package ar.com.coto.stock.adapter;

import ar.com.coto.stock.domain.model.PedidoRegistrado;
import ar.com.coto.stock.domain.port.IntegracionERP;
import java.util.Objects;

public final class AdaptadorERP implements IntegracionERP {
    private final SistemaERPExterno api;
    public AdaptadorERP(SistemaERPExterno api) { this.api = Objects.requireNonNull(api); }
    @Override public void enviarPedido(PedidoRegistrado p) {
        api.submitPurchaseOrder("COTO-" + p.id(), p.pedido().getStockId(), p.pedido().getCantidad(), p.pedido().getFecha().toInstant().toString());
    }
}
