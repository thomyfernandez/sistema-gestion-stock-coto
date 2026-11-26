package ar.com.coto.stock.domain.valorizacion;

import java.math.BigDecimal;

/** Descuento adicional por medio de pago sobre el resultado previo de la línea. */
public final class MedioPagoDecorator extends DecoradorPorcentual {
    public MedioPagoDecorator(ItemValorizable envuelto, BigDecimal porcentaje) {
        super(envuelto, porcentaje);
    }
}
