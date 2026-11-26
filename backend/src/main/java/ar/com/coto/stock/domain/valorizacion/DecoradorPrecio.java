package ar.com.coto.stock.domain.valorizacion;

import java.math.BigDecimal;
import java.util.Objects;

public abstract class DecoradorPrecio implements ItemValorizable {
    protected final ItemValorizable envuelto;

    protected DecoradorPrecio(ItemValorizable envuelto) { this.envuelto = Objects.requireNonNull(envuelto); }

    @Override public BigDecimal getImporte() { return envuelto.getImporte(); }
    @Override public int getCantidad() { return envuelto.getCantidad(); }
}
