package ar.com.coto.stock.domain.model;

import java.util.Date;
import java.util.Objects;

/** Sólo se pide mercadería simple al proveedor; los combos se descomponen antes. */
public final class PedidoReposicion {
    private final int stockId;
    private final Date fecha;
    private final int cantidad;
    private EstadoPedido estado = EstadoPedido.PENDIENTE;

    public PedidoReposicion(int stockId, Date fecha, int cantidad) {
        this.stockId = Reglas.positivo(stockId, "stockId");
        this.fecha = new Date(Objects.requireNonNull(fecha).getTime());
        this.cantidad = Reglas.positivo(cantidad, "cantidad");
    }

    public int getStockId() { return stockId; }
    public Date getFecha() { return new Date(fecha.getTime()); }
    public int getCantidad() { return cantidad; }
    public synchronized EstadoPedido getEstado() { return estado; }

    public synchronized void marcarEnviado() {
        exigirEstado(EstadoPedido.PENDIENTE);
        estado = EstadoPedido.ENVIADO;
    }

    public synchronized void marcarRecibido() {
        exigirEstado(EstadoPedido.ENVIADO);
        estado = EstadoPedido.RECIBIDO;
    }

    public synchronized void cancelar() {
        exigirEstado(EstadoPedido.PENDIENTE);
        estado = EstadoPedido.CANCELADO;
    }

    private void exigirEstado(EstadoPedido esperado) {
        if (estado != esperado) throw new IllegalStateException("El pedido debe estar " + esperado);
    }
}
