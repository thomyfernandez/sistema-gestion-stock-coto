package ar.com.coto.stock.domain.valorizacion;

import ar.com.coto.stock.domain.model.Reglas;
import java.math.BigDecimal;

/** Cada descuento actúa sobre el resultado anterior; los porcentajes no se suman. */
public final class DescuentoDecorator extends DecoradorPrecio {
    private final double porcentaje;

    public DescuentoDecorator(ItemValorizable envuelto, double porcentaje) {
        super(envuelto);
        this.porcentaje = Reglas.porcentaje(porcentaje);
    }

    @Override public double getPrecio() {
        return BigDecimal.valueOf(envuelto.getPrecio()).multiply(BigDecimal.ONE.subtract(
                BigDecimal.valueOf(porcentaje).movePointLeft(2))).doubleValue();
    }
}
