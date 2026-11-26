package ar.com.coto.stock.domain.valorizacion;

import ar.com.coto.stock.domain.model.Reglas;
import java.math.BigDecimal;
import java.math.MathContext;

/** Las unidades de una línea son homogéneas. Un 2x1 cobra ceil(cantidad / 2). */
public final class DosPorUnoDecorator extends DecoradorPrecio {
    public DosPorUnoDecorator(ItemValorizable envuelto) {
        super(envuelto);
    }

    @Override public BigDecimal getImporte() {
        int cantidad = Reglas.positivo(envuelto.getCantidad(), "cantidad");
        int cobradas = cantidad - cantidad / 2;
        return envuelto.getImporte()
                .multiply(BigDecimal.valueOf(cobradas))
                .divide(BigDecimal.valueOf(cantidad), MathContext.DECIMAL128);
    }
}
