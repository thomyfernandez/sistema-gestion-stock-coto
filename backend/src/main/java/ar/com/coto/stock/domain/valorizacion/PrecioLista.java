package ar.com.coto.stock.domain.valorizacion;

import ar.com.coto.stock.domain.model.Producto;
import ar.com.coto.stock.domain.model.Reglas;
import java.math.BigDecimal;
import java.util.Objects;

/** ConcreteComponent. Una unidad por defecto, o la cantidad de una línea del carrito. */
public final class PrecioLista implements ItemValorizable {
    private final Producto producto;
    private final int cantidad;

    public PrecioLista(Producto producto) { this(producto, 1); }

    public PrecioLista(Producto producto, int cantidad) {
        this.producto = Objects.requireNonNull(producto);
        this.cantidad = Reglas.positivo(cantidad, "cantidad");
        Reglas.noNegativo(getPrecio(), "total de lista");
    }

    @Override public double getPrecio() {
        return BigDecimal.valueOf(producto.getPrecio()).multiply(BigDecimal.valueOf(cantidad)).doubleValue();
    }

    @Override public int getCantidad() { return cantidad; }
}
