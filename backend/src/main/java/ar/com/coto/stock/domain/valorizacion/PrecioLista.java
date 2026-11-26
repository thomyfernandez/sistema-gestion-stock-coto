package ar.com.coto.stock.domain.valorizacion;

import ar.com.coto.stock.domain.model.Producto;
import ar.com.coto.stock.domain.model.Reglas;
import java.math.BigDecimal;
import java.util.Objects;

/** ConcreteComponent. Una unidad por defecto, o la cantidad de una línea del carrito. */
public final class PrecioLista implements ItemValorizable {
    private final BigDecimal importe;
    private final int cantidad;

    public PrecioLista(Producto producto) { this(producto, 1); }

    public PrecioLista(Producto producto, int cantidad) {
        this.cantidad = Reglas.positivo(cantidad, "cantidad");
        this.importe = BigDecimal.valueOf(Objects.requireNonNull(producto).getPrecio()).multiply(BigDecimal.valueOf(cantidad));
    }

    @Override public BigDecimal getImporte() { return importe; }

    @Override public int getCantidad() { return cantidad; }
}
