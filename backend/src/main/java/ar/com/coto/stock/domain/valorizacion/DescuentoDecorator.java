package ar.com.coto.stock.domain.valorizacion;

/** Descuento general del artículo. */
public final class DescuentoDecorator extends DecoradorPorcentual {
    public DescuentoDecorator(ItemValorizable envuelto, double porcentaje) {
        super(envuelto, porcentaje);
    }
}
