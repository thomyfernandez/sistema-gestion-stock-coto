package ar.com.coto.stock.domain.valorizacion;

/** Descuento adicional por medio de pago sobre el resultado previo de la línea. */
public final class MedioPagoDecorator extends DecoradorPorcentual {
    public MedioPagoDecorator(ItemValorizable envuelto, double porcentaje) {
        super(envuelto, porcentaje);
    }
}
