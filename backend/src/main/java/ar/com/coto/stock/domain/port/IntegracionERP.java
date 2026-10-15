package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.model.PedidoRegistrado;

@FunctionalInterface
public interface IntegracionERP { void enviarPedido(PedidoRegistrado pedido); }
