package ar.com.coto.stock.domain.valorizacion;

import ar.com.coto.stock.domain.model.Reglas;
import java.math.BigDecimal;
import java.math.MathContext;

/** Las unidades de una línea son homogéneas. Un 2x1 cobra ceil(cantidad / 2). */
public final class PromocionDecorator extends DecoradorPrecio {
    public static final String DOS_POR_UNO = "2x1";
    public static final String MEDIO_DE_PAGO = "Descuento Medio de Pago";
    private final String tipoPromo;
    private final double porcentajeMedioPago;

    public PromocionDecorator(ItemValorizable envuelto, String tipoPromo) {
        this(envuelto, tipoPromo, 0);
        if (MEDIO_DE_PAGO.equals(tipoPromo)) {
            throw new IllegalArgumentException("Indicá el porcentaje del medio de pago explícitamente");
        }
    }

    public PromocionDecorator(ItemValorizable envuelto, String tipoPromo, double porcentajeMedioPago) {
        super(envuelto);
        this.tipoPromo = Reglas.texto(tipoPromo, "tipoPromo");
        if (!DOS_POR_UNO.equals(this.tipoPromo) && !MEDIO_DE_PAGO.equals(this.tipoPromo)) {
            throw new IllegalArgumentException("Promoción desconocida: " + tipoPromo);
        }
        this.porcentajeMedioPago = Reglas.porcentaje(porcentajeMedioPago);
        if (DOS_POR_UNO.equals(this.tipoPromo) && porcentajeMedioPago != 0) {
            throw new IllegalArgumentException("El 2x1 no recibe un porcentaje adicional");
        }
    }

    @Override public double getPrecio() {
        BigDecimal base = BigDecimal.valueOf(envuelto.getPrecio());
        if (DOS_POR_UNO.equals(tipoPromo)) {
            int cantidad = Reglas.positivo(envuelto.getCantidad(), "cantidad");
            int cobradas = cantidad - cantidad / 2;
            return base.multiply(BigDecimal.valueOf(cobradas))
                    .divide(BigDecimal.valueOf(cantidad), MathContext.DECIMAL128).doubleValue();
        }
        return base.multiply(BigDecimal.ONE.subtract(
                BigDecimal.valueOf(porcentajeMedioPago).movePointLeft(2))).doubleValue();
    }
}
