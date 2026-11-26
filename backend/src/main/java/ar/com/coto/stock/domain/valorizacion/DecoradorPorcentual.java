package ar.com.coto.stock.domain.valorizacion;

import ar.com.coto.stock.domain.model.Reglas;
import java.math.BigDecimal;

/** Descuento porcentual sobre el resultado del decorador anterior; los porcentajes no se suman. */
public abstract class DecoradorPorcentual extends DecoradorPrecio {
    private final BigDecimal factor;

    protected DecoradorPorcentual(ItemValorizable envuelto, double porcentaje) {
        super(envuelto);
        this.factor = BigDecimal.ONE.subtract(BigDecimal.valueOf(Reglas.porcentaje(porcentaje)).movePointLeft(2));
    }

    @Override public final BigDecimal getImporte() {
        return envuelto.getImporte().multiply(factor);
    }
}
