package ar.com.coto.stock.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Comando inmutable que el futuro GestorInventario registrará y persistirá. */
public final class MovimientoStock {
    private final int stockId;
    private final TipoMovimiento tipo;
    private final int cantidad;
    private final Instant fecha;
    private final String motivo;
    private final String usuario;

    public MovimientoStock(int stockId, TipoMovimiento tipo, int cantidad,
                           Instant fecha, String motivo, String usuario) {
        this.stockId = Reglas.positivo(stockId, "stockId");
        this.tipo = Objects.requireNonNull(tipo);
        this.cantidad = Reglas.positivo(cantidad, "cantidad");
        this.fecha = Objects.requireNonNull(fecha);
        this.motivo = Reglas.texto(motivo, "motivo");
        this.usuario = Reglas.texto(usuario, "usuario");
    }

    public int getStockId() { return stockId; }
    public TipoMovimiento getTipo() { return tipo; }
    public int getCantidad() { return cantidad; }
    public Instant getFecha() { return fecha; }
    public String getMotivo() { return motivo; }
    public String getUsuario() { return usuario; }
}
