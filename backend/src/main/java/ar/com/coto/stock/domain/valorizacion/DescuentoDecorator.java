package ar.com.coto.stock.domain.valorizacion;

import java.math.BigDecimal;

/** Descuento general del artículo. */
public final class DescuentoDecorator extends DecoradorPorcentual {
    public DescuentoDecorator(ItemValorizable envuelto, BigDecimal porcentaje) {
        super(envuelto, porcentaje);
    }
}
